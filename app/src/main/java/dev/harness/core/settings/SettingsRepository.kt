package dev.harness.core.settings

import android.content.Context
import dev.harness.core.keys.SecretStore

/**
 * Provider catalogue for the MVP: API-key authentication, injected as
 * environment variables into the Pi process (PROMPT §6).
 */
data class Provider(
    val id: String,
    val label: String,
    val envVar: String,
    val keyHint: String,
    val defaultModel: String? = null,
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

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(v) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT, v).apply()

    fun apiKey(providerId: String): String? {
        val p = Providers.byId(providerId) ?: return null
        return secrets.get(p.envVar)
    }

    fun setApiKey(providerId: String, key: String) {
        val p = Providers.byId(providerId) ?: return
        secrets.put(p.envVar, key.trim())
    }

    /** Env map injected into Pi for the currently selected provider. */
    fun apiKeyEnv(): Map<String, String> {
        val out = HashMap<String, String>()
        for (p in Providers.ALL) {
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
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_BATTERY_PROMPT = "battery_prompt_shown"
    }
}
