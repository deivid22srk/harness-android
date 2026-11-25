package dev.harness.core.pi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Pure codec for the Pi's declared packages (`packages` array in
 * `~/.pi/agent/settings.json` / `.pi/settings.json`). Source of truth:
 * docs/packages.md + experiments on Pi 1.6.0 — entries are strings
 * (`"npm:foo@1.0.0"`) or objects with a `source` key and optional filters.
 * JVM-testable: File + strings only.
 */
object PackageDeclaredCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /** Raw declared sources of a scope, in file order. */
    fun parse(text: String): List<String> {
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val packages = obj["packages"] as? JsonArray ?: return emptyList()
        return packages.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.content
                is JsonObject -> (el["source"] as? JsonPrimitive)?.content
                else -> null
            }
        }
    }

    /** Declared sources of a scope, read from the settings file (missing → empty). */
    fun readFile(file: File): List<String> = runCatching {
        if (!file.exists()) emptyList() else parse(file.readText())
    }.getOrDefault(emptyList())

    /**
     * Builds the enriched [InstalledPackage] list for one scope, resolving
     * features from the installed copy when available (npm dir / git dir).
     */
    fun buildList(
        sources: List<String>,
        scope: PackageScope,
        npmModulesDir: File?,
        gitDir: File?,
    ): List<InstalledPackage> = sources.map { source ->
        val parsed = PackageSources.parse(source)
        val dir = when (parsed.kind) {
            PackageKind.NPM -> npmModulesDir?.let { File(File(it, "node_modules"), parsed.identity) }
            PackageKind.GIT -> gitDir?.let { File(it, parsed.identity.replace(Regex("[^A-Za-z0-9._-]"), "-")) }
            else -> null
        }
        val meta = dir?.let(::readMeta)
        InstalledPackage(
            source = source,
            scope = scope,
            kind = parsed.kind,
            identity = parsed.identity,
            ref = parsed.ref,
            pinned = parsed.ref != null,
            features = meta?.first ?: emptySet(),
            description = meta?.second,
            installPath = dir?.absolutePath,
        )
    }

    /** (features, description) from `package.json` `pi` key or conventional dirs. */
    fun readMeta(dir: File): Pair<Set<PackageFeature>, String?>? {
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
        return features to description
    }
}
