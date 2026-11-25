package dev.harness.core.sessions

import dev.harness.core.projects.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests the Pi session-file parser against REAL fixtures produced from the
 * documented 1.6.0 format (docs/session-format.md) — including truncated,
 * corrupt, header-only and empty files (PROMPT §4.4).
 */
class SessionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repo: SessionRepository

    private val project: Project
        get() = Project("demo", "/root/projects/demo", File(tmp.root, "home/projects/demo").absolutePath, false, 0)

    private fun homeDir(): File = tmp.newFolder("home")

    private fun fixture(name: String): String {
        val stream = javaClass.getResourceAsStream("/sessions/$name") ?: error("fixture missing: $name")
        return stream.use { it.readBytes().decodeToString() }
    }

    private fun installSessionDir(name: String): File {
        val dir = repo.sessionDirForGuest("/root/projects/demo")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(fixture(name))
        return file
    }

    @Test
    fun `session dir name follows the Pi convention`() {
        val r = SessionRepository(homeDir(), tmp.newFolder("state"))
        assertEquals("--root-projects-demo--", r.sessionDirNameForGuest("/root/projects/demo"))
        assertEquals("--root-projects-Meu.Proj-01--", r.sessionDirNameForGuest("/root/projects/Meu.Proj-01"))
        assertEquals("--tmp-x-y--", r.sessionDirNameForGuest("/tmp/x/y"))
    }

    @Test
    fun `host guest path conversion round-trips`() {
        val home = homeDir()
        val r = SessionRepository(home, tmp.newFolder("state"))
        val guest = "/root/.pi/agent/sessions/--root-projects-demo--/a.jsonl"
        val host = r.guestToHostPath(guest)
        assertEquals(File(home, ".pi/agent/sessions/--root-projects-demo--/a.jsonl").absolutePath, host)
        assertEquals(guest, r.hostToGuestPath(host))
    }

    @Test
    fun `valid session summary has name count model and title from session_info`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        installSessionDir("valid-session.jsonl")

        val list = runBlocking { repo.listSessions(project) }
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("Correção de login", s.title)
        assertTrue(s.isNamed)
        assertEquals("aaaa1111-2222-3333-4444-555555555555", s.sessionId)
        assertEquals(3, s.messageCount) // user(1) + assistant(2); toolResult/usage excluded
        assertEquals("openai/gpt-5", s.model) // last model_change wins
        assertFalse(s.corrupt)
        assertFalse(s.truncated)
    }

    @Test
    fun `untitled session falls back to the first user text`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        installSessionDir("untitled-session.jsonl")

        val s = runBlocking { repo.listSessions(project) }[0]
        assertEquals("Explique este código", s.title)
        assertFalse(s.isNamed)
        assertEquals("google/gemini-2.5-pro", s.model) // from the assistant message
    }

    @Test
    fun `truncated file degrades without throwing and keeps parsed prefix`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        installSessionDir("truncated-session.jsonl")

        val s = runBlocking { repo.listSessions(project) }[0]
        assertEquals("bbbb1111-2222-3333-4444-555555555555", s.sessionId)
        assertEquals(1, s.messageCount) // the cut assistant line is ignored
        assertEquals("teste truncado", s.title)
        assertFalse(s.corrupt)
    }

    @Test
    fun `corrupt file is flagged and has no usable data`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        installSessionDir("corrupt-session.jsonl")

        val s = runBlocking { repo.listSessions(project) }[0]
        assertTrue(s.corrupt)
        assertEquals(0, s.messageCount)
        assertEquals("Sessão", s.title)
    }

    @Test
    fun `header-only and empty files do not break the list`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        installSessionDir("header-only.jsonl")
        installSessionDir("empty-session.jsonl")

        val list = runBlocking { repo.listSessions(project) }
        assertEquals(2, list.size)
        assertTrue(list.all { !it.corrupt || it.messageCount == 0 })
    }

    @Test
    fun `sessions are listed newest first and isolated per project`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        val dir = repo.sessionDirForGuest("/root/projects/demo")
        dir.mkdirs()
        val old = File(dir, "2026-11-20T10-00-00-000Z_a.jsonl")
        val new = File(dir, "2026-11-21T10-00-00-000Z_b.jsonl")
        old.writeText(fixture("valid-session.jsonl"))
        new.writeText(fixture("untitled-session.jsonl"))
        old.setLastModified(1_000_000)
        new.setLastModified(2_000_000)

        val list = runBlocking { repo.listSessions(project) }
        assertEquals(new.absolutePath, list[0].file)

        // Another project must see NOTHING from this directory.
        val other = Project("outro", "/root/projects/outro", "/tmp/outro", false, 0)
        assertTrue(runBlocking { repo.listSessions(other) }.isEmpty())
    }

    @Test
    fun `last session marker round-trips and detects deleted files`() {
        val home = homeDir()
        val state = tmp.newFolder("state")
        repo = SessionRepository(home, state)
        val file = File(home, ".pi/agent/sessions/--root-projects-demo--/a.jsonl")
        file.parentFile.mkdirs()
        file.writeText(fixture("header-only.jsonl"))

        repo.setLastSessionFile(project, file.absolutePath)
        assertEquals(file.absolutePath, repo.lastSessionFileFor(project))

        file.delete()
        assertNull(repo.lastSessionFileFor(project))
    }

    @Test
    fun `rename appends a session_info entry that the parser then reads`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        val file = installSessionDir("valid-session.jsonl")

        val summary = repo.summarize(file)
        val result = runBlocking { repo.renameSession(summary, "Novo nome") }
        assertTrue(result is RenameResult.Success)

        val after = repo.summarize(file)
        assertEquals("Novo nome", after.title)
        // Original entries intact (append-only).
        assertTrue(file.readText().contains("\"Correção de login\""))
        val lines = file.readLines()
        val last = kotlinx.serialization.json.Json.parseToJsonElement(
            lines.last(),
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("session_info", (last["type"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertNotNull(last["parentId"])
    }

    @Test
    fun `rename rejects blank and oversized names`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        val file = installSessionDir("valid-session.jsonl")
        val summary = repo.summarize(file)

        assertTrue(runBlocking { repo.renameSession(summary, "   ") } is RenameResult.Failure)
        assertTrue(runBlocking { repo.renameSession(summary, "x".repeat(200)) } is RenameResult.Failure)
        // Original name untouched after failures.
        assertEquals("Correção de login", repo.summarize(file).title)
    }

    @Test
    fun `newest session file returns the only existing session`() {
        repo = SessionRepository(homeDir(), tmp.newFolder("state"))
        assertNull(repo.newestSessionFile(project))
        installSessionDir("valid-session.jsonl")
        assertEquals("aaaa1111", repo.newestSessionFile(project)?.let { repo.summarize(File(it)).sessionId.take(8) })
    }
}
