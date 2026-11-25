package dev.harness.core.pi

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProotCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * AgentSession against a FAKE Pi process (PROMPT §4.4): handshake ok/timeout,
 * NoProvider decision, process death with restart+backoff, credential swap
 * preserving the session, bounded stderr tail. Robolectric provides Context.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 34: matches the screenshots pin. Robolectric's default (targetSdk 36)
// requires Java 21, but the CI test JVM is Java 17.
@org.robolectric.annotation.Config(sdk = [34])
class AgentSessionTest {

    private lateinit var paths: HarnessPaths
    private lateinit var proot: ProotCommand

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        paths = HarnessPaths(app)
        paths.ensureDirs()
        // Markers so hasProotBinaries() is true in the sandbox.
        File(paths.nativeLibDir, "libproot.so").apply { parentFile?.mkdirs() }.writeText("fake")
        File(paths.nativeLibDir, "libproot-loader.so").writeText("fake")
        proot = ProotCommand(paths)
    }

    // -------------------------------------------------------------------
    // Fake process plumbing
    // -------------------------------------------------------------------

    /**
     * In-memory stand-in for the PRoot/Node process: reads JSONL commands and
     * answers with [responder]-provided records; stderr optionally noisy.
     */
    private class FakePi(
        private val responder: (String) -> List<String>,
        private val noise: Int = 0,
    ) : Process(), PiProcessHandle {
        // session writes commands -> shellOut reader; shell writes responses <- shellIn
        private val sessionToShellIn = PipedInputStream(1024 * 1024)
        private val sessionOut = PipedOutputStream(sessionToShellIn)
        private val shellToSessionIn = PipedInputStream(1024 * 1024)
        private val shellOut = PipedOutputStream(shellToSessionIn)
        private val sessionErrIn = PipedInputStream(1024 * 1024)
        private val shellErr = PipedOutputStream(sessionErrIn)

        @Volatile private var killed = false
        private val lock = Object()
        val commands = ConcurrentLinkedQueue<String>()
        var startCount = 0
            private set

        private val reader = Thread {
            try {
                val buf = StringBuilder()
                val chunk = ByteArray(4096)
                while (!killed) {
                    val n = sessionToShellIn.read(chunk)
                    if (n < 0) break
                    buf.append(String(chunk, 0, n))
                    var idx: Int
                    while (buf.indexOf("\n").also { idx = it } >= 0) {
                        val line = buf.substring(0, idx).removeSuffix("\r").trim()
                        buf.delete(0, idx + 1)
                        if (line.isBlank()) continue
                        commands += line
                        val json = runCatching {
                            Json.parseToJsonElement(line) as JsonObject
                        }.getOrNull() ?: continue
                        val type = (json["type"] as? JsonPrimitive)?.content ?: continue
                        val id = (json["id"] as? JsonPrimitive)?.content ?: continue
                        for (response in responder(type)) {
                            shellOut.write(withId(response, id).toByteArray())
                            shellOut.write('\n'.code)
                        }
                        shellOut.flush()
                    }
                }
            } catch (_: Throwable) {
                // pipes closed on kill
            }
        }

        private fun withId(response: String, id: String): String {
            val obj = Json.parseToJsonElement(response).let { it as JsonObject }
            return Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("id", id)
                obj.forEach { (k, v) -> put(k, v) }
            })
        }

        override fun start(config: PiLaunchConfig) {
            startCount++
            killed = false
            if (!reader.isAlive) reader.start()
            if (noise > 0) {
                Thread {
                    runCatching {
                        repeat(noise) { i ->
                            shellErr.write("noise $i\n".toByteArray())
                        }
                        shellErr.flush()
                    }
                }.start()
            }
        }

        override fun stop() = destroy()

        override fun getOutputStream(): OutputStream = sessionOut
        override fun getInputStream(): InputStream = shellToSessionIn
        override fun getErrorStream(): InputStream = sessionErrIn

        override fun waitFor(): Int {
            synchronized(lock) { while (!killed) lock.wait() }
            return exitValue()
        }

        override fun exitValue(): Int = if (killed) 137 else throw IllegalThreadStateException()
        // PiProcessHandle surface: `process` (val) and `isAlive` (fun) are
        // explicit overrides — the fun form avoids the JVM signature clash
        // with the inherited java.lang.Process#isAlive().
        override val process: Process? get() = this
        override fun isAlive(): Boolean = !killed
        override fun destroy() {
            killed = true
            synchronized(lock) { lock.notifyAll() }
            runCatching { sessionOut.close() }
            runCatching { shellToSessionIn.close() }
            runCatching { sessionToShellIn.close() }
        }

        override fun destroyForcibly(): Process = this.also { destroy() }
    }

    private class FakeFactory : (PiLaunchConfig) -> PiProcessHandle {
        val created = ConcurrentLinkedQueue<FakePi>()
        var responder: (String) -> List<String> = { emptyList() }
        var noise = 0
        override fun invoke(config: PiLaunchConfig): PiProcessHandle {
            val p = FakePi(responder, noise)
            created += p
            p.start(config)
            return p
        }
    }

    private fun okResponder(): (String) -> List<String> = { type ->
        when (type) {
            "get_state" -> listOf(
                """
                {"type":"response","command":"get_state","success":true,"data":{
                  "sessionFile":"/root/.pi/agent/sessions/--root--/abc.jsonl",
                  "sessionId":"abc","sessionName":"Sessão teste","messageCount":2,
                  "model":{"provider":"anthropic","id":"claude-sonnet-4-5","name":"claude","reasoning":false}
                }}
                """.trimIndent(),
            )
            "get_available_models" -> listOf(
                """
                {"type":"response","command":"get_available_models","success":true,"data":{
                  "models":[{"provider":"anthropic","id":"claude-sonnet-4-5","name":"claude","reasoning":false}]
                }}
                """.trimIndent(),
            )
            "get_messages" -> listOf(
                """
                {"type":"response","command":"get_messages","success":true,"data":{"messages":[]}}
                """.trimIndent(),
            )
            "get_available_thinking_levels" -> listOf(
                """
                {"type":"response","command":"get_available_thinking_levels","success":true,"data":{"levels":["off","medium"]}}
                """.trimIndent(),
            )
            "switch_session" -> listOf(
                """
                {"type":"response","command":"switch_session","success":true,"data":{"cancelled":false}}
                """.trimIndent(),
            )
            else -> listOf("""{"type":"response","command":"$type","success":true}""")
        }
    }

    private suspend fun awaitConnection(
        session: AgentSession,
        expected: ConnectionState,
        timeoutMs: Long = 5000,
    ) {
        withTimeout(timeoutMs) {
            while (session.connection.value != expected) delay(20)
        }
    }

    private fun launchConfig(withKeys: Boolean) = PiLaunchConfig(
        apiKeys = if (withKeys) mapOf("ANTHROPIC_API_KEY" to "sk-ant-test") else emptyMap(),
    )

    // -------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------

    @Test
    fun `handshake ok with key reaches Connected`() = runBlocking {
        val factory = FakeFactory().apply { responder = okResponder() }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        val started = session.start(launchConfig(withKeys = true))
        assertTrue(started)
        awaitConnection(session, ConnectionState.Connected)
        assertEquals("/root/.pi/agent/sessions/--root--/abc.jsonl", session.state.value.sessionFile)
        assertEquals("Sessão teste", session.state.value.sessionName)
        assertEquals("claude-sonnet-4-5", session.state.value.model?.id)
        session.stop()
    }

    @Test
    fun `handshake without any key reaches NoProvider`() = runBlocking {
        val factory = FakeFactory().apply { responder = okResponder() }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        val started = session.start(launchConfig(withKeys = false))
        assertTrue(started)
        awaitConnection(session, ConnectionState.NoProvider)
        session.stop()
    }

    @Test
    fun `handshake timeout surfaces Failed with diagnostic`() = runBlocking {
        val factory = FakeFactory().apply { responder = { emptyList() } } // never answers
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 300, restartBaseMs = 20,
        )
        val started = session.start(launchConfig(withKeys = true))
        assertEquals(false, started)
        awaitConnection(session, ConnectionState.Failed)
        assertTrue(session.connectionInfo.value.lastError!!.contains("handshake"))
        session.stop()
    }

    @Test
    fun `process death triggers bounded restart and recovers`() = runBlocking {
        val factory = FakeFactory().apply { responder = okResponder() }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        session.start(launchConfig(withKeys = true))
        awaitConnection(session, ConnectionState.Connected)

        // Kill -9 equivalent: watchdog must notice and restart (Connected
        // again via a NEW fake process; the Restarting window is ~restartBaseMs
        // so we await the recovered state instead of the transient one).
        factory.created.peek()!!.destroy()
        // The watchdog notices the death asynchronously — the state may still
        // read Connected for a few ticks, so poll for the replacement process.
        withTimeout(8000) {
            while (factory.created.size < 2) delay(20)
        }
        awaitConnection(session, ConnectionState.Connected, timeoutMs = 8000)
        assertTrue(factory.created.size >= 2)
        session.stop()
    }

    @Test
    fun `applyCredentials preserves the active session`() = runBlocking {
        val factory = FakeFactory().apply { responder = okResponder() }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        session.start(launchConfig(withKeys = false))
        awaitConnection(session, ConnectionState.NoProvider)
        val fileBefore = session.state.value.sessionFile!!

        val ok = session.applyCredentials(launchConfig(withKeys = true))
        assertTrue(ok)
        awaitConnection(session, ConnectionState.Connected, timeoutMs = 8000)

        // The switch lands on the replacement process; the command is recorded
        // by its reader thread, so poll briefly before asserting.
        var switchCommands = 0
        withTimeout(8000) {
            while (switchCommands == 0) {
                switchCommands = factory.created.drop(1)
                    .flatMap { it.commands }
                    .count { it.contains("\"switch_session\"") }
                if (switchCommands == 0) delay(20)
            }
        }
        assertTrue("switch_session deve ser reemitido após o reinício", switchCommands >= 1)
        assertEquals(fileBefore, session.state.value.sessionFile)
        session.stop()
    }

    @Test
    fun `stderr tail is bounded at 2000 lines`() = runBlocking {
        val factory = FakeFactory().apply {
            responder = okResponder()
            noise = 2400
        }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        session.start(launchConfig(withKeys = true))
        awaitConnection(session, ConnectionState.Connected)
        // Drain race: wait until the tail reaches its bound.
        withTimeout(5000) {
            while (session.stderrLog().lineCount() < 2000) delay(25)
        }
        assertEquals(2000, session.stderrLog().lineCount())
        assertTrue(session.stderrLog().contains("noise 2399"))
        session.stop()
    }

    private fun String.lineCount(): Int = split('\n').size

    @Test
    fun `session survives state read after restart`() = runBlocking {
        val factory = FakeFactory().apply { responder = okResponder() }
        val session = AgentSession(
            paths, proot, CoroutineScope(SupervisorJob() + Dispatchers.IO),
            processFactory = factory, handshakeTimeoutMs = 1500, restartBaseMs = 20,
        )
        session.start(launchConfig(withKeys = true))
        awaitConnection(session, ConnectionState.Connected)
        // chat state must have the model hydrated by the handshake
        withTimeout(5000) {
            while (session.state.value.model == null) delay(20)
        }
        session.stop()
        assertEquals(ConnectionState.Stopped, session.connection.first())
    }
}
