package dev.harness.core.settings

import dev.harness.core.env.ProcessRunner
import dev.harness.core.env.ProotCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** One model as reported by `pi --list-models`. */
data class ModelEntry(
    val provider: String,
    val id: String,
    val context: String,
    val thinking: Boolean,
    val images: Boolean,
) {
    val qualifiedId: String get() = "$provider/$id"
}

/**
 * Bridges the app's provider/model configuration to Pi:
 *
 * - Writes `~/.pi/agent/models.json` describing custom/compatible endpoints so
 *   `pi` can reach them with the same API-key environment variables.
 * - Lists models through `pi --list-models` (authoritative for the installed
 *   Pi version, including bundled catalogs and models.json entries).
 */
class ModelCatalog(
    private val paths: dev.harness.core.env.HarnessPaths,
    private val proot: ProotCommand,
    private val settings: SettingsRepository,
) {
    private val modelsJson: File get() = File(paths.piDir, "agent/models.json")

    /**
     * Writes models.json from the configured providers.
     *
     * Pi requires a custom provider to declare at least one model, otherwise it
     * has nothing to offer and `--list-models`/startup report nothing for it.
     * Each listed model ID becomes an entry so Pi's catalog is populated.
     */
    fun syncModelsJson() {
        val providers = linkedMapOf<String, Any>()
        for (custom in settings.customProviders()) {
            val modelIds = custom.models
                .split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val models = modelIds.map { id ->
                linkedMapOf<String, Any>(
                    "id" to id,
                    "name" to id,
                )
            }
            val entry = linkedMapOf<String, Any>(
                "name" to custom.label,
                "baseUrl" to custom.baseUrl,
                "api" to custom.api,
                "apiKey" to "\$${custom.envVar}",
            )
            if (models.isNotEmpty()) entry["models"] = models
            providers[custom.id] = entry
        }
        modelsJson.parentFile?.mkdirs()
        val root = linkedMapOf<String, Any>(
            "\$schema" to "https://pi.dev/schemas/models.schema.json",
            "providers" to providers,
        )
        modelsJson.writeText(JSONObject(root).toString(2))
    }

    /** Runs `pi --list-models` and parses the table output. */
    suspend fun listModels(apiKeys: Map<String, String> = emptyMap()): List<ModelEntry> =
        withContext(Dispatchers.IO) {
            syncModelsJson()
            val cmd = listOf("pi", "--list-models")
            val result = ProcessRunner.runCombined(proot.argv(cmd), proot.hostEnv(apiKeys))
            parseTable(result.output)
        }

    private fun parseTable(output: String): List<ModelEntry> {
        val entries = ArrayList<ModelEntry>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach
            // Skip header and separators.
            if (trimmed.startsWith("provider") || trimmed.all { it == '-' || it == ' ' }) return@forEach
            val cols = trimmed.split(Regex("\\s{2,}"))
            if (cols.size < 2) return@forEach
            val provider = cols[0]
            val id = cols[1]
            if (provider.isBlank() || id.isBlank()) return@forEach
            entries += ModelEntry(
                provider = provider,
                id = id,
                context = cols.getOrNull(2) ?: "",
                thinking = cols.getOrNull(4)?.trim().equals("yes", ignoreCase = true),
                images = cols.getOrNull(5)?.trim().equals("yes", ignoreCase = true),
            )
        }
        return entries.sortedWith(compareBy({ it.provider }, { it.id }))
    }

    /** Builds the `--models` scope argument from the user's selection. */
    fun modelsScopeArg(providerId: String): List<String> {
        val selected = settings.selectedModels(providerId)
        if (selected.isEmpty()) return emptyList()
        val patterns = selected.joinToString(",")
        return listOf("--models", patterns)
    }
}
