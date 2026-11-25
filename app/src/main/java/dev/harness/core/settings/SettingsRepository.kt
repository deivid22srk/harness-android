package dev.harness.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.harness.core.keys.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Provider catalogue for the MVP: API-key authentication, injected as
 * environment variables into the Pi process (PROMPT §6).
 *
 * `api` values must be APIs Pi understands (`openai-completions`,
 * `openai-responses`, `anthropic-messages`, `google-generative-ai`, …). See
 * docs/pi/models-configuration.md.
 */
data class Provider(
    val id: String,
    val label: String,
    val envVar: String,
    val keyHint: String,
    val defaultModel: String? = null,
    /** Pi API id for custom endpoints; null for Pi's built-in providers. */
    val api: String? = null,
    /** Base URL for custom endpoints; null for Pi's built-in providers. */
    val baseUrl: String? = null,
)

/** A user-defined provider stored in settings (DataStore, JSON-encoded). */
@Serializable
data class CustomProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val api: String,
    val envVar: String,
    /** Comma/newline separated model IDs this endpoint exposes (required by Pi). */
    val models: String = "",
)

object Providers {
    val ALL = listOf(
        Provider("anthropic", "Anthropic", "ANTHROPIC_API_KEY", "sk-ant-…", "claude-sonnet-4-20250514"),
        Provider("openai", "OpenAI", "OPENAI_API_KEY", "sk-…", "gpt-5"),
        Provider("google", "Google", "GEMINI_API_KEY", "AIza…"),
        Provider("openrouter", "OpenRouter", "OPENROUTER_API_KEY", "sk-or-…"),
        Provider("groq", "Groq", "GROQ_API_KEY", "gsk_…"),
        Provider("xai", "xAI", "XAI_API_KEY", "xai-…"),
        Provider("mistral", "Mistral", "MISTRAL_API_KEY", ""),
        Provider("cerebras", "Cerebras", "CEREBRAS_API_KEY", "csk-…"),
        Provider("deepseek", "DeepSeek", "DEEPSEEK_API_KEY", "sk-…"),
    )

    /** APIs Pi supports for a compatible endpoint. */
    val APIS = listOf(
        "openai-completions",
        "openai-responses",
        "anthropic-messages",
        "google-generative-ai",
    )

    fun byId(id: String): Provider? = ALL.firstOrNull { it.id == id }
}

/**
 * Typed persistence of user preferences backed by **Jetpack DataStore**
 * (PROMPT §6.2): immutable [AppSettings] model, explicit defaults, one-shot
 * migration from the legacy SharedPreferences file — **without ever touching
 * the API keys**, which live in the Keystore-backed [SecretStore].
 *
 * Call sites read synchronously (an in-memory snapshot hydrated in `init`)
 * and write through suspend-backed setters that update the snapshot first
 * and persist asynchronously — keeping the existing ViewModels unchanged
 * while the durable storage becomes typed and schema-friendly.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    val secrets = SecretStore(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var snap: AppSettings = AppSettings()

    /** Current typed snapshot (already includes migrated values). */
    val current: AppSettings get() = snap

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "harness_settings")
    private val store = appContext.dataStore

    init {
        snap = runBlocking { loadWithMigration() }
    }

    // ------------------------------------------------------------------
    // Load + migration
    // ------------------------------------------------------------------

    private suspend fun loadWithMigration(): AppSettings = mutex.withLock {
        val stored = store.data.first()
        if (stored.contains(KEY_MIGRATED)) {
            return@withLock decodeSettings(stored)
        }
        // First run on the new storage: import the legacy SharedPreferences
        // (values that exist; anything absent keeps the typed default).
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val imported = buildFromLegacy(legacy)
        store.edit {
            it.clear()
            writeSettings(it, imported)
            it[KEY_MIGRATED] = true
        }
        // Clear the legacy file so stale values can never resurface. API keys
        // are in SecretStore, not here — nothing is lost.
        legacy.edit().clear().commit()
        imported
    }

    private fun buildFromLegacy(prefs: android.content.SharedPreferences): AppSettings {
        val selected = HashMap<String, List<String>>()
        for (p in Providers.ALL) {
            val raw = prefs.getString("selected_models_" + p.id, null) ?: continue
            selected[p.id] = raw.split('\n').filter { it.isNotBlank() }
        }
        val customRaw = prefs.getString("custom_providers", null)
        return AppSettings(
            provider = prefs.getString("provider", null) ?: "anthropic",
            model = prefs.getString("model", null) ?: "",
            thinking = prefs.getString("thinking", null) ?: "medium",
            themeMode = ThemeMode.fromId(prefs.getString("theme", null).orEmpty()),
            dynamicColor = prefs.getBoolean("dynamic_color", false),
            fontScale = prefs.getFloat("font_scale", 1f),
            resumeLastSession = prefs.getBoolean("resume_last_session", true),
            onboardingDone = prefs.getBoolean("onboarded", false),
            batteryPromptShown = prefs.getBoolean("battery_prompt_shown", false),
            customProviders = customRaw?.let(::decodeLegacyCustom).orEmpty(),
            selectedModels = selected,
        )
    }

    /** Decodes the legacy \u0001/\u0002 custom-provider encoding. */
    private fun decodeLegacyCustom(raw: String): List<CustomProvider> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u0001').mapNotNull { entry ->
            val parts = entry.split('\u0002')
            if (parts.size < 5) return@mapNotNull null
            CustomProvider(
                id = parts[0],
                label = parts[1],
                baseUrl = parts[2],
                api = parts[3],
                envVar = parts[4],
                models = parts.getOrNull(5).orEmpty(),
            )
        }
    }

    private fun decodeSettings(prefs: Preferences): AppSettings {
        val selected = prefs[KEY_SELECTED_MODELS]
            ?.mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) null else entry.take(idx) to entry.substring(idx + 1).split('\n').filter { it.isNotBlank() }
            }
            ?.toMap()
            .orEmpty()
        return AppSettings(
            provider = prefs[KEY_PROVIDER] ?: "anthropic",
            model = prefs[KEY_MODEL] ?: "",
            thinking = prefs[KEY_THINKING] ?: "medium",
            themeMode = ThemeMode.fromId(prefs[KEY_THEME].orEmpty()),
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            fontScale = prefs[KEY_FONT_SCALE] ?: 1f,
            resumeLastSession = prefs[KEY_RESUME_SESSION] ?: true,
            onboardingDone = prefs[KEY_ONBOARDED] ?: false,
            batteryPromptShown = prefs[KEY_BATTERY_PROMPT] ?: false,
            customProviders = prefs[KEY_CUSTOM_PROVIDERS]?.let { raw ->
                runCatching { json.decodeFromString<List<CustomProvider>>(raw) }.getOrDefault(emptyList())
            }.orEmpty(),
            selectedModels = selected,
        )
    }

    private fun writeSettings(target: androidx.datastore.preferences.core.MutablePreferences, value: AppSettings) {
        target[KEY_PROVIDER] = value.provider
        target[KEY_MODEL] = value.model
        target[KEY_THINKING] = value.thinking
        target[KEY_THEME] = value.themeMode.id
        target[KEY_DYNAMIC] = value.dynamicColor
        target[KEY_FONT_SCALE] = value.fontScale
        target[KEY_RESUME_SESSION] = value.resumeLastSession
        target[KEY_ONBOARDED] = value.onboardingDone
        target[KEY_BATTERY_PROMPT] = value.batteryPromptShown
        target[KEY_CUSTOM_PROVIDERS] = json.encodeToString(value.customProviders)
        target[KEY_SELECTED_MODELS] = value.selectedModels.map { (k, v) -> "$k=${v.joinToString("\n")}" }.toSet()
    }

    /** Updates the snapshot immediately and persists asynchronously. */
    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(snap)
        snap = next
        scope.launch {
            mutex.withLock {
                store.edit { writeSettings(it, snap) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Typed accessors (sync facade over the snapshot)
    // ------------------------------------------------------------------

    var provider: String
        get() = snap.provider
        set(v) = update { it.copy(provider = v) }

    var model: String
        get() = snap.model
        set(v) = update { it.copy(model = v) }

    var thinking: String
        get() = snap.thinking
        set(v) = update { it.copy(thinking = v) }

    /** Theme id ("system" | "light" | "dark") for the existing UI call sites. */
    var themeMode: String
        get() = snap.themeMode.id
        set(v) = update { it.copy(themeMode = ThemeMode.fromId(v)) }

    var dynamicColor: Boolean
        get() = snap.dynamicColor
        set(v) = update { it.copy(dynamicColor = v) }

    var fontScale: Float
        get() = snap.fontScale
        set(v) = update { it.copy(fontScale = v) }

    var resumeLastSession: Boolean
        get() = snap.resumeLastSession
        set(v) = update { it.copy(resumeLastSession = v) }

    var autoScroll: Boolean
        get() = snap.autoScroll
        set(v) = update { it.copy(autoScroll = v) }

    var renderMarkdown: Boolean
        get() = snap.renderMarkdown
        set(v) = update { it.copy(renderMarkdown = v) }

    var sendOnEnter: Boolean
        get() = snap.sendOnEnter
        set(v) = update { it.copy(sendOnEnter = v) }

    var hapticFeedback: Boolean
        get() = snap.hapticFeedback
        set(v) = update { it.copy(hapticFeedback = v) }

    var onboardingDone: Boolean
        get() = snap.onboardingDone
        set(v) = update { it.copy(onboardingDone = v) }

    var batteryPromptShown: Boolean
        get() = snap.batteryPromptShown
        set(v) = update { it.copy(batteryPromptShown = v) }

    // ---------------------------------------------------------------------
    // Custom providers
    // ---------------------------------------------------------------------

    /** All providers (built-in + custom), as seen by the settings UI. */
    fun allProviders(): List<Provider> = Providers.ALL + snap.customProviders.map { it.asProvider() }

    fun providerById(id: String): Provider? = allProviders().firstOrNull { it.id == id }

    fun customProviders(): List<CustomProvider> = snap.customProviders

    fun addCustomProvider(provider: CustomProvider) {
        update { it.copy(customProviders = it.customProviders.filterNot { c -> c.id == provider.id } + provider) }
    }

    fun removeCustomProvider(id: String) {
        update { it.copy(customProviders = it.customProviders.filterNot { c -> c.id == id }) }
        secrets.put(envVarFor(id), "")
    }

    private fun envVarFor(id: String): String =
        "HARNESS_KEY_" + id.uppercase().replace(Regex("[^A-Z0-9]"), "_")

    private fun CustomProvider.asProvider() = Provider(
        id = id,
        label = label,
        envVar = envVar,
        keyHint = "chave do provedor",
        api = api,
        baseUrl = baseUrl,
    )

    // ---------------------------------------------------------------------
    // Selected models (per provider)
    // ---------------------------------------------------------------------

    /** Model IDs the user chose to keep for a provider; empty means "all". */
    fun selectedModels(providerId: String): Set<String> =
        snap.selectedModels[providerId]?.toSet().orEmpty()

    fun setSelectedModels(providerId: String, ids: Set<String>) {
        update { it.copy(selectedModels = it.selectedModels + (providerId to ids.toList())) }
    }

    fun hasModelSelection(providerId: String): Boolean = snap.selectedModels.containsKey(providerId)

    // ---------------------------------------------------------------------
    // API keys (Keystore-backed, never in DataStore)
    // ---------------------------------------------------------------------

    fun apiKey(providerId: String): String? {
        val p = providerById(providerId) ?: return null
        return secrets.get(p.envVar)
    }

    fun setApiKey(providerId: String, key: String) {
        val p = providerById(providerId) ?: return
        secrets.put(p.envVar, key.trim())
    }

    /** Env map injected into Pi for every configured provider. */
    fun apiKeyEnv(): Map<String, String> {
        val out = HashMap<String, String>()
        for (p in allProviders()) {
            secrets.get(p.envVar)?.takeIf { it.isNotBlank() }?.let { out[p.envVar] = it }
        }
        return out
    }

    fun hasAnyKey(): Boolean = apiKeyEnv().isNotEmpty()

    private companion object {
        const val LEGACY_PREFS = "harness_settings"

        val KEY_MIGRATED = booleanPreferencesKey("migrated_from_prefs")
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_THINKING = stringPreferencesKey("thinking")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val KEY_RESUME_SESSION = booleanPreferencesKey("resume_last_session")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_BATTERY_PROMPT = booleanPreferencesKey("battery_prompt_shown")
        val KEY_CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
        val KEY_SELECTED_MODELS = stringSetPreferencesKey("selected_models")

}
}
