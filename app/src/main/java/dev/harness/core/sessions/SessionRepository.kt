package dev.harness.core.sessions

import dev.harness.core.projects.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.SecureRandom

/**
 * Immutable summary of one Pi session file, built by scanning the JSONL
 * header and lightweight entries only. The file on disk remains the single
 * source of truth (PROMPT §2.5) — nothing here copies conversation content.
 */
data class SessionSummary(
    /** Host-side absolute path of the .jsonl file. */
    val file: String,
    /** Session id from the header (UUID). */
    val sessionId: String,
    /** User-defined name (session_info), or null when never named. */
    val name: String?,
    /** First user message text — fallback title when [name] is null. */
    val firstUserText: String?,
    /** Count of user+assistant messages actually written to the file. */
    val messageCount: Int,
    /** `provider/modelId` of the last model_change or assistant message. */
    val model: String?,
    /** File mtime (ms) — sessions are append-only, so this is the last activity. */
    val updatedAt: Long,
    /** True when the scan hit the byte cap and counts may be incomplete. */
    val truncated: Boolean,
    /** True when the header is missing or the file could not be parsed. */
    val corrupt: Boolean,
) {
    /** Title shown in lists: name, else first user text, else "Sessão". */
    val title: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: firstUserText?.takeIf { it.isNotBlank() }
            ?: "Sessão"

    /** True when this session has a user-defined name. */
    val isNamed: Boolean get() = !name.isNullOrBlank()
}

/** One-shot result of a rename so the UI can report precisely. */
sealed interface RenameResult {
    data class Success(val summary: SessionSummary) : RenameResult
    data class Failure(val message: String) : RenameResult
}

/**
 * Lists and manages Pi session files **without starting the Pi**.
 *
 * Sessions live under `~/.pi/agent/sessions/--<cwd>--/<timestamp>_<id>.jsonl`
 * (docs `session-format.md`, verified against Pi 1.6.0). The mapping from a
 * project guest path to that directory is deterministic — see
 * [sessionDirNameForGuest] — which is what makes "sessões por projeto"
 * reliable: the same project always maps to the same directory, on disk.
 *
 * Parsing is lazy and defensive: summary-relevant entries are decoded after a
 * cheap contains() gate, message lines are counted under a per-file byte cap,
 * and any malformed or truncated file degrades to a [SessionSummary] with
 * `corrupt = true` instead of throwing.
 */
class SessionRepository(
    /** Host path bound to the guest `/root` (bind-mount of HOME). */
    private val home: File,
    /** App-owned state directory (active-session marker). */
    private val stateDir: File,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private val sessionsRoot: File get() = File(File(File(home, ".pi"), "agent"), "sessions")

    /**
     * Pi's own directory convention (docs session-format.md: "removes the
     * leading path separator and replaces `/`, `\\` and `:` with `-`",
     * wrapped in `--`…`--`). Verified empirically on Pi 1.6.0.
     */
    fun sessionDirNameForGuest(guestPath: String): String {
        val stripped = guestPath.removePrefix("/")
        val encoded = stripped.replace(Regex("[\\\\/:]"), "-")
        return "--$encoded--"
    }

    /** Host directory holding the sessions of a project guest path. */
    fun sessionDirForGuest(guestPath: String): File =
        File(sessionsRoot, sessionDirNameForGuest(guestPath))

    /** Converts a host session file path into the path the Pi sees (guest). */
    fun hostToGuestPath(hostPath: String): String =
        "/root" + hostPath.removePrefix(home.absolutePath)

    /** Converts a guest path reported by `get_state.sessionFile` to host. */
    fun guestToHostPath(guestPath: String): String =
        home.absolutePath + guestPath.removePrefix("/root")

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /**
     * Lists the sessions of a project, newest first. Only files under the
     * project's deterministic session directory are returned, so sessions of
     * different projects never mix (acceptance §4.1.6).
     */
    suspend fun listSessions(project: Project, limit: Int = DEFAULT_LIST_LIMIT): List<SessionSummary> =
        withContext(Dispatchers.IO) { listSessionsBlocking(project, limit) }

    fun listSessionsBlocking(project: Project, limit: Int = DEFAULT_LIST_LIMIT): List<SessionSummary> {
        val dir = sessionDirForGuest(project.guestPath)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") } ?: return emptyList()
        return files.asSequence()
            .map { summarize(it) }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .toList()
    }

    /** Newest session file of a project (used for the first resume attempt). */
    fun newestSessionFile(project: Project): String? =
        listSessionsBlocking(project, limit = 1).firstOrNull()?.file

    /**
     * "Limpar sessões antigas": deletes all but the newest [keep] sessions of
     * a project. Never touches the active/last-resumed file even when older.
     * Returns the number of files deleted.
     */
    suspend fun deleteOldSessions(
        project: Project,
        keep: Int,
        activeFile: String?,
    ): Int = withContext(Dispatchers.IO) {
        val summaries = listSessionsBlocking(project)
        val toDelete = summaries
            .drop(keep)
            .filter { it.file != activeFile }
            .map { File(it.file) }
        var deleted = 0
        for (f in toDelete) {
            if (f.delete()) deleted++
        }
        deleted
    }

    // ------------------------------------------------------------------
    // Persistence of the active session per project
    // ------------------------------------------------------------------

    private val lastSessionFile: File
        get() = File(stateDir, "last-session.properties")

    /** Returns the persisted active session file for a project, or null. */
    fun lastSessionFileFor(project: Project): String? {
        val props = readLastSessionProps()
        val stored = props.getProperty(project.hostPath) ?: return null
        return stored.takeIf { File(it).exists() }
    }

    fun setLastSessionFile(project: Project, hostSessionFile: String?) {
        val props = readLastSessionProps()
        if (hostSessionFile == null) {
            props.remove(project.hostPath)
        } else {
            props.setProperty(project.hostPath, hostSessionFile)
        }
        lastSessionFile.parentFile?.mkdirs()
        lastSessionFile.outputStream().use { props.store(it, "project -> active session file (host path)") }
    }

    private fun readLastSessionProps(): java.util.Properties = runCatching {
        if (!lastSessionFile.exists()) return java.util.Properties()
        lastSessionFile.inputStream().use { java.util.Properties().apply { load(it) } }
    }.getOrDefault(java.util.Properties())

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /** Deletes the session file. Returns false when the file was already gone. */
    suspend fun deleteSession(summary: SessionSummary): Boolean = withContext(Dispatchers.IO) {
        File(summary.file).delete()
    }

    /**
     * Renames a session by appending a `session_info` entry — the same
     * append-only mechanism the Pi itself uses (docs session-format.md).
     * Works without starting the Pi, so closed sessions can be renamed too.
     */
    suspend fun renameSession(summary: SessionSummary, newName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val file = File(summary.file)
            if (!file.exists()) return@withContext RenameResult.Failure("A sessão não existe mais.")
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@withContext RenameResult.Failure("O nome não pode ficar vazio.")
            if (trimmed.length > MAX_NAME_LENGTH) {
                return@withContext RenameResult.Failure("O nome deve ter até $MAX_NAME_LENGTH caracteres.")
            }
            val leafId = lastEntryId(file)
                ?: return@withContext RenameResult.Failure("Arquivo de sessão inválido ou vazio.")
            val entry = buildJsonObject {
                put("type", "session_info")
                put("id", randomEntryId())
                put("parentId", leafId)
                put("timestamp", isoNow())
                put("name", trimmed)
            }
            runCatching {
                file.appendText(json.encodeToString(JsonObject.serializer(), entry) + "\n")
            }.fold(
                onSuccess = { RenameResult.Success(summary.copy(name = trimmed)) },
                onFailure = { RenameResult.Failure(it.message ?: "Falha ao gravar o arquivo de sessão.") },
            )
        }

    /** Last entry id of the file (the tree leaf) — null when unreadable/empty. */
    private fun lastEntryId(file: File): String? = runCatching {
        file.useLines { lines ->
            var id: String? = null
            lines.forEach { line ->
                if (line.length < MIN_ENTRY_LINE) return@forEach
                val parsed = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject
                    ?: return@forEach
                (parsed["id"] as? JsonPrimitive)?.content?.let { id = it }
            }
            id
        }
    }.getOrNull()

    // ------------------------------------------------------------------
    // Summarization
    // ------------------------------------------------------------------

    /**
     * Scans one session file. Only lines whose `type` matters to the summary
     * are JSON-parsed (a cheap contains() gate first), keeping large sessions
     * cheap. A byte cap bounds the worst case and flags [SessionSummary.truncated].
     */
    fun summarize(file: File): SessionSummary {
        var sessionId = ""
        var headerTimestamp: Long? = null
        var name: String? = null
        var firstUserText: String? = null
        var messageCount = 0
        var model: String? = null
        var truncated = false
        var sawAnyEntry = false
        var headerSeen = false

        runCatching {
            file.inputStream().use { input ->
                val reader = input.bufferedReader(Charsets.UTF_8)
                var bytes = 0L
                while (true) {
                    val line = reader.readLine() ?: break
                    bytes += line.length + 1
                    if (bytes > MAX_SCAN_BYTES) {
                        truncated = true
                        break
                    }
                    if (line.length < MIN_ENTRY_LINE) continue
                    // Cheap gate before any JSON parse: only lines that carry
                    // one of the four summary-relevant entry types are decoded.
                    // Whitespace-tolerant because JSON.stringify in the Pi is
                    // compact, but external files may not be.
                    if (!ENTRY_GATE.containsMatchIn(line)) continue

                    val obj = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: continue
                    sawAnyEntry = true
                    // Dispatch on the PARSED type — substring gates can over-
                    // match ("session_info" contains "session"), parsed types
                    // cannot.
                    when ((obj["type"] as? JsonPrimitive)?.content) {
                        "session" -> {
                            headerSeen = true
                            sessionId = obj.stringAt("id").orEmpty()
                            headerTimestamp = isoToMs(obj.stringAt("timestamp"))
                        }

                        "session_info" -> name = obj.stringAt("name") ?: name

                        "model_change" -> {
                            val p = obj.stringAt("provider")
                            val m = obj.stringAt("modelId")
                            if (p != null && m != null) model = "$p/$m"
                        }

                        "message" -> {
                            val msg = obj["message"] as? JsonObject
                            if (msg != null) when (msg.stringAt("role")) {
                                "user" -> {
                                    messageCount++
                                    if (firstUserText == null) firstUserText = msg.userText()
                                }

                                "assistant" -> {
                                    messageCount++
                                    if (model == null) {
                                        val p = msg.stringAt("provider")
                                        val m = msg.stringAt("model")
                                        if (p != null && m != null) model = "$p/$m"
                                    }
                                }

                                else -> Unit
                                // toolResult/system/custom and unknown roles are
                                // not part of the user-facing message count.
                            }
                        }

                        else -> Unit // over-matched gate (e.g. quoted text inside content)
                    }
                }
            }
        }

        return SessionSummary(
            file = file.absolutePath,
            sessionId = sessionId,
            name = name,
            firstUserText = firstUserText?.let { it.replace('\n', ' ').trim().take(TITLE_MAX) },
            messageCount = messageCount,
            model = model,
            updatedAt = file.lastModified().takeIf { it > 0L } ?: headerTimestamp ?: 0L,
            truncated = truncated,
            corrupt = !headerSeen && !sawAnyEntry,
        )
    }

    companion object {
        private const val DEFAULT_LIST_LIMIT = 200
        private const val MAX_SCAN_BYTES = 8L * 1024 * 1024
        private const val MIN_ENTRY_LINE = 8
        private const val TITLE_MAX = 120
        const val MAX_NAME_LENGTH = 80

        /** Pre-filter for entry types the summary needs (see summarize). */
        private val ENTRY_GATE = Regex(
            "\"type\"\\s*:\\s*\"(session|message|session_info|model_change)\"",
        )

        private fun JsonObject.stringAt(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

        private fun isoToMs(iso: String?): Long? = iso?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }

        /** Content of the first text block of a user message (title fallback). */
        private fun JsonObject.userText(): String? {
            val content = this["content"] ?: return null
            return when (content) {
                is JsonPrimitive -> content.content
                is kotlinx.serialization.json.JsonArray -> content.firstNotNullOfOrNull { block ->
                    val b = block as? JsonObject ?: return@firstNotNullOfOrNull null
                    if (b.stringAt("type") == "text") b.stringAt("text") else null
                }

                else -> null
            }
        }

        private fun isoNow(): String = java.time.Instant.now().toString()

        private fun randomEntryId(): String {
            val hex = "0123456789abcdef"
            val rnd = SecureRandom()
            return buildString { repeat(8) { append(hex[rnd.nextInt(16)]) } }
        }
    }
}
