package dev.harness.core.settings

import android.content.Context
import dev.harness.core.keys.SecretStore

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

/** A user-defined provider stored in preferences. */
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

/** Non-secret, user-visible preferences. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("harness_settings", Context.MODE_PRIVATE)
    val secrets = SecretStore(context)

    var provider: String
        get() = prefs.getString(KEY_PROVIDER, "anthropic") ?: "anthropic"
        set(v) = prefs.edit().putString(KEY_PROVIDER, v).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MODEL, v).apply()

    var thinking: String
        get() = prefs.getString(KEY_THINKING, "medium") ?: "medium"
        set(v) = prefs.edit().putString(KEY_THINKING, v).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /** Material You dynamic palette (API 31+); falls back to brand colors. */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC, v).apply()

    /** User font-size multiplier applied on top of the system font scale. */
    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1f)
        set(v) = prefs.edit().putFloat(KEY_FONT_SCALE, v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(v) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT, v).apply()

    /**
     * Sessions: retomar automaticamente a última sessão ao abrir um projeto
     * (padrão: true — com atalho visível para ver todas, PROMPT §4.1.2).
     */
    var resumeLastSession: Boolean
        get() = prefs.getBoolean(KEY_RESUME_SESSION, true)
        set(v) = prefs.edit().putBoolean(KEY_RESUME_SESSION, v).apply()

    // ---------------------------------------------------------------------
    // Custom providers
    // ---------------------------------------------------------------------

    /** All providers (built-in + custom), as seen by the settings UI. */
    fun allProviders(): List<Provider> = Providers.ALL + customProviders().map { it.asProvider() }

    fun providerById(id: String): Provider? = allProviders().firstOrNull { it.id == id }

    fun customProviders(): List<CustomProvider> {
        val raw = prefs.getString(KEY_CUSTOM_PROVIDERS, null) ?: return emptyList()
        return decodeCustom(raw)
    }

    fun addCustomProvider(provider: CustomProvider) {
        val list = customProviders().filterNot { it.id == provider.id } + provider
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, encodeCustom(list)).apply()
    }

    fun removeCustomProvider(id: String) {
        val list = customProviders().filterNot { it.id == id }
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, encodeCustom(list)).apply()
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

    private fun encodeCustom(list: List<CustomProvider>): String =
        list.joinToString("\u0001") {
            listOf(it.id, it.label, it.baseUrl, it.api, it.envVar, it.models).joinToString("\u0002")
        }

    private fun decodeCustom(raw: String): List<CustomProvider> {
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

    // ---------------------------------------------------------------------
    // Selected models (per provider)
    // ---------------------------------------------------------------------

    /** Model IDs the user chose to keep for a provider; empty means "all". */
    fun selectedModels(providerId: String): Set<String> {
        val raw = prefs.getString(KEY_SELECTED_PREFIX + providerId, null) ?: return emptySet()
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }

    fun setSelectedModels(providerId: String, ids: Set<String>) {
        prefs.edit().putString(KEY_SELECTED_PREFIX + providerId, ids.joinToString("\n")).apply()
    }

    fun hasModelSelection(providerId: String): Boolean =
        prefs.contains(KEY_SELECTED_PREFIX + providerId)

    // ---------------------------------------------------------------------
    // API keys
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
        const val KEY_PROVIDER = "provider"
        const val KEY_MODEL = "model"
        const val KEY_THINKING = "thinking"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_BATTERY_PROMPT = "battery_prompt_shown"
        const val KEY_RESUME_SESSION = "resume_last_session"
        const val KEY_CUSTOM_PROVIDERS = "custom_providers"
        const val KEY_SELECTED_PREFIX = "selected_models_"
    }
}
