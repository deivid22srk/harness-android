package dev.harness.core.settings

import kotlinx.serialization.Serializable

/** Theme preference for the app (not the Pi). */
enum class ThemeMode(val id: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Immutable, typed snapshot of every user preference (PROMPT §6.2).
 *
 * Defaults are declared here — the single source for "what happens on first
 * run". Persisted via Jetpack DataStore; the legacy SharedPreferences file is
 * migrated once and then cleared. API keys are NOT here: they live encrypted
 * in `SecretStore` (Keystore) and are never touched by the migration.
 */
@Serializable
data class AppSettings(
    // Provider / model
    val provider: String = "anthropic",
    val model: String = "",
    val thinking: String = "medium",
    // Appearance
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    // Chat
    val autoScroll: Boolean = true,
    val renderMarkdown: Boolean = true,
    val sendOnEnter: Boolean = false,
    val hapticFeedback: Boolean = true,
    // Projects & sessions
    val resumeLastSession: Boolean = true,
    // Onboarding / one-shot prompts
    val onboardingDone: Boolean = false,
    val batteryPromptShown: Boolean = false,
    // Custom providers + per-provider model selection
    val customProviders: List<CustomProvider> = emptyList(),
    val selectedModels: Map<String, List<String>> = emptyMap(),
)
