package dev.harness.core.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class RpcClientTest {

    @Test
    fun `correlates responses by id`() = runBlocking {
        // Client stdin -> server reader
        val clientStdin = PipedOutputStream()
        val serverReader = PipedInputStream(clientStdin, 1 shl 16)
        // Server writer -> client stdout
        val serverWriter = PipedOutputStream()
        val clientStdout = PipedInputStream(serverWriter, 1 shl 16)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = RpcClient(stdin = clientStdin, stdout = clientStdout, scope = scope)
        client.start()

        val server = scope.launch(Dispatchers.IO) {
            val reader = serverReader.bufferedReader()
            val line = reader.readLine() ?: return@launch
            val id = Regex("\"id\":\"([^\"]+)\"").find(line)?.groupValues?.get(1)
            val response =
                "{\"id\":\"$id\",\"type\":\"response\",\"command\":\"get_state\",\"success\":true," +
                    "\"data\":{\"thinkingLevel\":\"medium\"}}\n"
            serverWriter.write(response.toByteArray())
            serverWriter.flush()
        }

        val response = client.call(buildJsonObject { put("type", "get_state") }, timeoutMs = 5_000)
        assertNotNull(response)
        assertEquals("get_state", response!!.command)
        assertEquals("medium", response.dataString("thinkingLevel"))

        server.cancel()
        client.close()
        scope.cancel()
    }

    @Test
    fun `dispatches events to the records flow`() = runBlocking {
        val clientStdin = PipedOutputStream()
        val serverWriter = PipedOutputStream()
        val clientStdout = PipedInputStream(serverWriter, 1 shl 16)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = RpcClient(stdin = clientStdin, stdout = clientStdout, scope = scope)

        val received = ArrayList<PiEvent>()
        val job = scope.launch {
            client.records.collect { record ->
                if (record is PiEventRecord) received += record.event
            }
        }
        client.start()
        serverWriter.write(
            "{\"type\":\"agent_start\"}\n{\"type\":\"agent_settled\",\"aborted\":false}\n".toByteArray(),
        )
        serverWriter.flush()
        delay(400)
        assertEquals(2, received.size)
        assertTrue(received[0] is PiEvent.AgentStart)
        assertTrue(received[1] is PiEvent.AgentSettled)
        job.cancel()
        client.close()
        scope.cancel()
    }
}
