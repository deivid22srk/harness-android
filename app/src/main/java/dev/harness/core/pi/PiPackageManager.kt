package dev.harness.core.pi

import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProcessRunner
import dev.harness.core.env.ProotCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Scope of a package declaration (global `~/.pi/agent/settings.json` or project `.pi/settings.json`). */
enum class PackageScope { GLOBAL, PROJECT }

/** Origin of a package source (docs/packages.md "Choose a source"). */
enum class PackageKind { NPM, GIT, URL, LOCAL }

/** Resource types a package can offer (manifest `pi` key or conventional dirs). */
enum class PackageFeature { EXTENSIONS, SKILLS, PROMPTS, THEMES }

/**
 * One installed package, parsed from the Pi settings (`packages` array) and
 * enriched with the metadata readable from its installed directory.
 */
data class InstalledPackage(
    val source: String,
    val scope: PackageScope,
    val kind: PackageKind,
    /** npm name, repo URL (git/https) or absolute path — the package identity. */
    val identity: String,
    /** Version/ref when pinned (`npm:name@1.2.3`, `git:repo@v1`); null otherwise. */
    val ref: String?,
    val pinned: Boolean,
    val features: Set<PackageFeature>,
    val description: String? = null,
    val installPath: String? = null,
)

/** npm registry search result for the explorer (per the user-provided doc). */
data class PackageSearchResult(
    val name: String,
    val version: String,
    val description: String?,
    val publisher: String?,
    val date: String?,
    val monthlyDownloads: Long,
    val features: Set<PackageFeature>,
    val npmUrl: String,
) {
    /** `pi install npm:<name>` — the command the explorer offers. */
    val installCommand: String get() = "npm:$name"
}

/** Live state of one package operation (install/remove/update). */
data class PackageOperation(
    val kind: OpKind,
    val target: String,
    val log: List<String>,
    val running: Boolean,
    val exitCode: Int? = null,
) {
    enum class OpKind { INSTALL, REMOVE, UPDATE }
}

/**
 * Manages Pi packages **exactly the way the Pi CLI does** (source of truth:
 * Pi 1.6.0 `pi install/remove/list/update --help` + docs/packages.md, verified
 * against the installed version):
 *
 * - The declared state lives in `~/.pi/agent/settings.json` (global) or
 *   `.pi/settings.json` (project, `-l`). This manager READS those files
 *   directly (host side, the rootfs is accessible) instead of parsing the
 *   fragile `pi list` output; the CLI runs inside PRoot for mutations.
 * - `pi update` BARE updates Pi itself (docs cli.md) — this manager only ever
 *   runs `pi update --extensions` (all packages) or `pi update <source>`.
 * - One operation at a time ([opMutex]); the agent being busy must be checked
 *   by the caller (PackagesViewModel) before destructive operations.
 */
class PiPackageManager(
    private val paths: HarnessPaths,
    private val proot: ProotCommand,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes install/remove/update — one operation at a time (§5.3.6). */
    private val opMutex = Mutex()

    /** Process of the running CLI op, for real cancellation. */
    @Volatile
    private var currentProcess: Process? = null

    private val _operation = MutableStateFlow<PackageOperation?>(null)
    val operation: StateFlow<PackageOperation?> = _operation.asStateFlow()

    /** Kills the running CLI op (npm install/remove/update) immediately. */
    fun cancelCurrent() {
        runCatching { currentProcess?.destroyForcibly() }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private val globalSettingsFile: File get() = File(File(paths.home, ".pi/agent"), "settings.json")

    private fun projectSettingsFile(projectHostPath: String): File =
        File(File(projectHostPath, ".pi"), "settings.json")

    private val npmDir: File get() = File(File(File(paths.home, ".pi"), "agent"), "npm")

    private val gitDir: File get() = File(File(File(paths.home, ".pi"), "agent"), "git")

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /**
     * Lists the packages declared for a scope, enriched when the installed
     * package dir exposes its features. Reading is file-only (no Pi process).
     */
    suspend fun listInstalled(projectHostPath: String?): List<InstalledPackage> = withContext(Dispatchers.IO) {
        val out = ArrayList<InstalledPackage>()
        readDeclared(globalSettingsFile, PackageScope.GLOBAL, out)
        if (projectHostPath != null) {
            readDeclared(projectSettingsFile(projectHostPath), PackageScope.PROJECT, out)
        }
        out
    }

    private fun readDeclared(file: File, scope: PackageScope, out: MutableList<InstalledPackage>) {
        val obj = runCatching {
            json.parseToJsonElement(file.readText()) as? JsonObject
        }.getOrNull() ?: return
        val packages = obj["packages"] as? kotlinx.serialization.json.JsonArray ?: return
        for (el in packages) {
            when (el) {
                is JsonPrimitive -> out += buildPackage(el.content, scope)
                is JsonObject -> {
                    val source = (el["source"] as? JsonPrimitive)?.content ?: continue
                    out += buildPackage(source, scope)
                }
                else -> Unit
            }
        }
    }

    /** Parses `npm:name[@ver]`, `git:...[@ref]`, `https://...` and local paths. */
    fun buildPackage(source: String, scope: PackageScope): InstalledPackage {
        val (kind, identity, ref) = parseSource(source)
        val pinned = ref != null
        val dir = installDirOf(kind, identity)
        val meta = dir?.let { readPackageMeta(it) }
        return InstalledPackage(
            source = source,
            scope = scope,
            kind = kind,
            identity = identity,
            ref = ref,
            pinned = pinned,
            features = meta?.features ?: emptySet(),
            description = meta?.description,
            installPath = dir?.absolutePath,
        )
    }

    data class ParsedSource(val kind: PackageKind, val identity: String, val ref: String?)

    fun parseSource(source: String): ParsedSource = PackageSources.parse(source)

    private fun installDirOf(kind: PackageKind, identity: String): File? = when (kind) {
        PackageKind.NPM -> File(File(npmDir, "node_modules"), identity)
        PackageKind.GIT -> File(gitDir, identity.replace(Regex("[^A-Za-z0-9._-]"), "-"))
        else -> null
    }

    private class PackageMeta(val features: Set<PackageFeature>, val description: String?)

    /** Reads `pi` manifest key and conventional dirs from the installed copy. */
    private fun readPackageMeta(dir: File): PackageMeta? {
        if (!dir.exists()) return null
        val manifest = runCatching {
            json.parseToJsonElement(File(dir, "package.json").readText()) as? JsonObject
        }.getOrNull()
        val description = (manifest?.get("description") as? JsonPrimitive)?.content
        val features = LinkedHashSet<PackageFeature>()
        (manifest?.get("pi") as? JsonObject)?.let { pi ->
            if (pi.containsKey("extensions")) features += PackageFeature.EXTENSIONS
            if (pi.containsKey("skills")) features += PackageFeature.SKILLS
            if (pi.containsKey("prompts")) features += PackageFeature.PROMPTS
            if (pi.containsKey("themes")) features += PackageFeature.THEMES
        }
        if (features.isEmpty()) {
            if (File(dir, "extensions").isDirectory) features += PackageFeature.EXTENSIONS
            if (File(dir, "skills").isDirectory) features += PackageFeature.SKILLS
            if (File(dir, "prompts").isDirectory) features += PackageFeature.PROMPTS
            if (File(dir, "themes").isDirectory) features += PackageFeature.THEMES
        }
        return PackageMeta(features, description)
    }

    // ------------------------------------------------------------------
    // Mutations (run the real pi CLI inside PRoot, streaming output)
    // ------------------------------------------------------------------

    /** Validation of the source field (acceptance §5.3.2). */
    fun validateSource(source: String): String? {
        val parsed = parseSource(source)
        return when (parsed.kind) {
            PackageKind.NPM -> if (parsed.identity.isBlank()) "Informe o nome do pacote após npm:" else null
            PackageKind.GIT -> if (!parsed.identity.contains('/')) "Use git:github.com/usuario/repositorio" else null
            PackageKind.URL -> if (!parsed.identity.startsWith("https://") && !parsed.identity.startsWith("http://")) {
                "URL inválida"
            } else {
                null
            }

            PackageKind.LOCAL -> if (parsed.identity.isBlank()) "Informe o caminho local" else null
        }
    }

    /**
     * Runs `pi install <source> [-l]`. Streams the CLI output through
     * [PiPackageManager.operation]; use [onLine] for the caller-side log.
     */
    fun install(source: String, local: Boolean, projectGuestPath: String?): Flow<String> = mutate(PackageOperation.OpKind.INSTALL, source) {
        val argv = mutableListOf("pi", "install", source)
        if (local) argv += "-l"
        runPi(argv, projectGuestPath)
    }

    /** Runs `pi remove <source> [-l]`. */
    fun remove(source: String, local: Boolean, projectGuestPath: String?): Flow<String> = mutate(PackageOperation.OpKind.REMOVE, source) {
        val argv = mutableListOf("pi", "remove", source)
        if (local) argv += "-l"
        runPi(argv, projectGuestPath)
    }

    /**
     * Runs `pi update --extensions` (all unpinned packages) or
     * `pi update <source>` (one). NEVER bare `pi update` (that updates Pi).
     */
    fun update(source: String?, projectGuestPath: String?): Flow<String> = mutate(
        PackageOperation.OpKind.UPDATE,
        source ?: "--extensions",
    ) {
        val argv = if (source == null) {
            listOf("pi", "update", "--extensions")
        } else {
            listOf("pi", "update", source)
        }
        runPi(argv, projectGuestPath)
    }

    private fun runPi(argv: List<String>, projectGuestPath: String?): ProcessRunner.Result {
        // The project cwd matters for `-l` (pi writes .pi/settings.json there).
        val guestCwd = projectGuestPath ?: "/root"
        return ProcessRunner.runCombined(
            proot.argv(argv, guestEnv = emptyMap(), workDir = guestCwd),
            proot.hostEnv(emptyMap()),
            workDir = paths.filesDir,
            onStarted = { currentProcess = it },
        )
    }

    /**
     * Shared mutation runner: takes the op mutex, publishes a live
     * [PackageOperation] with each output line, then completes with the
     * final state (exit code) so the collector can read it once.
     */
    private fun mutate(
        kind: PackageOperation.OpKind,
        target: String,
        block: () -> ProcessRunner.Result,
    ): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        opMutex.withLock {
            _operation.value = PackageOperation(kind, target, emptyList(), running = true)
            try {
                val result = withContext(Dispatchers.IO) { block() }
                val lines = result.output.lines().filter { it.isNotBlank() }
                for (line in lines) send(line)
                _operation.value = PackageOperation(kind, target, lines, running = false, exitCode = result.exitCode)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    _operation.value = PackageOperation(kind, target, emptyList(), running = false, exitCode = -1)
                    throw t
                }
                val message = t.message ?: "Falha inesperada"
                send(message)
                _operation.value = PackageOperation(kind, target, listOf(message), running = false, exitCode = -1)
            } finally {
                currentProcess = null
            }
        }
    }

    // ------------------------------------------------------------------
    // npm registry search (explorer — per the user-provided packages doc)
    // ------------------------------------------------------------------

    /**
     * Searches `keywords:pi-package <query>` on the npm registry search API
     * (`GET /-/v1/search`), exactly as documented in the user's packages file:
     * no official pi.dev API exists (501/404 verified there); results and
     * ordering come from npm and may differ from the pi.dev gallery.
     */
    suspend fun searchRegistry(query: String, from: Int = 0, size: Int = 60): List<PackageSearchResult> =
        withContext(Dispatchers.IO) {
            val text = if (query.isBlank()) {
                "keywords:pi-package"
            } else {
                "keywords:pi-package ${query.trim()}"
            }
            val url = "https://registry.npmjs.org/-/v1/search?text=" +
                URLEncoder.encode(text, "UTF-8") + "&size=$size&from=$from"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            try {
                if (conn.responseCode != 200) {
                    error("npm respondeu HTTP ${conn.responseCode}")
                }
                val body = conn.inputStream.bufferedReader().readText()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return@withContext emptyList()
                val objects = root["objects"] as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList()
                objects.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val pkg = obj["package"] as? JsonObject ?: return@mapNotNull null
                    val name = (pkg["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val keywords = (pkg["keywords"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content }
                        .orEmpty()
                    val features = LinkedHashSet<PackageFeature>()
                    if ("pi-extension" in keywords) features += PackageFeature.EXTENSIONS
                    if ("pi-skill" in keywords) features += PackageFeature.SKILLS
                    if ("pi-prompt" in keywords) features += PackageFeature.PROMPTS
                    if ("pi-theme" in keywords) features += PackageFeature.THEMES
                    val links = pkg["links"] as? JsonObject
                    PackageSearchResult(
                        name = name,
                        version = (pkg["version"] as? JsonPrimitive)?.content.orEmpty(),
                        description = (pkg["description"] as? JsonPrimitive)?.content,
                        publisher = ((pkg["publisher"] as? JsonObject)?.get("username") as? JsonPrimitive)?.content,
                        date = (pkg["date"] as? JsonPrimitive)?.content,
                        monthlyDownloads = ((obj["downloads"] as? JsonObject)?.get("monthly") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                        features = features,
                        npmUrl = (links?.get("npm") as? JsonPrimitive)?.content
                            ?: "https://www.npmjs.com/package/$name",
                    )
                }
            } finally {
                conn.disconnect()
            }
        }
}

/** Pure parser of Pi package source strings (docs/packages.md) — JVM-testable. */
object PackageSources {
    fun parse(source: String): PiPackageManager.ParsedSource {
        val trimmed = source.trim()
        return when {
            trimmed.startsWith("npm:") -> {
                val rest = trimmed.removePrefix("npm:")
                val at = rest.lastIndexOf('@')
                if (at > 0 && rest.substring(at + 1).isNotBlank()) {
                    PiPackageManager.ParsedSource(PackageKind.NPM, rest.substring(0, at), rest.substring(at + 1))
                } else {
                    PiPackageManager.ParsedSource(PackageKind.NPM, rest.removeSuffix("@"), null)
                }
            }

            trimmed.startsWith("git:") -> {
                val rest = trimmed.removePrefix("git:")
                val at = rest.lastIndexOf('@')
                if (at > 0 && rest.substring(at + 1).isNotBlank()) {
                    PiPackageManager.ParsedSource(PackageKind.GIT, rest.substring(0, at), rest.substring(at + 1))
                } else {
                    PiPackageManager.ParsedSource(PackageKind.GIT, rest, null)
                }
            }

            trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                PiPackageManager.ParsedSource(PackageKind.URL, trimmed, null)

            else -> PiPackageManager.ParsedSource(PackageKind.LOCAL, trimmed, null)
        }
    }
}
