package dev.harness

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.harness.core.env.ProvisionState
import dev.harness.core.pi.ConnectionState
import dev.harness.core.projects.Project
import dev.harness.core.rpc.ExtensionUiRequest
import dev.harness.core.settings.CustomProvider
import dev.harness.core.settings.Provider
import dev.harness.core.state.ChatState
import dev.harness.service.AgentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { Onboarding, Projects, Chat, Settings }

/** A model listed in the "Obter modelos" dialog. */
data class ModelOption(
    val provider: String,
    val id: String,
    val selected: Boolean,
) {
    val qualifiedId: String get() = "$provider/$id"
}

data class HarnessUiState(
    val screen: Screen = Screen.Onboarding,
    val provision: ProvisionState = ProvisionState.Idle,
    val connection: ConnectionState = ConnectionState.Stopped,
    val chat: ChatState = ChatState(),
    val projects: List<Project> = emptyList(),
    val cloning: Boolean = false,
    val projectsError: String? = null,
    val activeProject: Project? = null,
    val providers: List<Provider> = emptyList(),
    val selectedProvider: String = "anthropic",
    val thinkingLevel: String = "medium",
    val showModelPicker: Boolean = false,
    val showThinkingPicker: Boolean = false,
    val showAttachSheet: Boolean = false,
    val diagnostics: String = "",
    val notice: String? = null,
    val pendingUi: List<ExtensionUiRequest> = emptyList(),
    // "Obter modelos" dialog
    val modelsDialogProvider: String? = null,
    val modelsLoading: Boolean = false,
    val models: List<ModelOption> = emptyList(),
    val modelsError: String? = null,
)

class HarnessViewModel(private val app: Application) : ViewModel() {

    private val container = (app as HarnessApp).container
    private val appContext: Context get() = app.applicationContext

    private val _ui = MutableStateFlow(HarnessUiState())
    val uiState: StateFlow<HarnessUiState> = _ui.asStateFlow()

    init {
        val settings = container.settings
        val alreadyProvisioned = container.provisioner.isProvisioned()
        _ui.value = _ui.value.copy(
            providers = settings.allProviders(),
            selectedProvider = settings.provider,
            thinkingLevel = settings.thinking,
            provision = container.provisioner.state.value,
            screen = if (alreadyProvisioned) Screen.Projects else Screen.Onboarding,
        )

        viewModelScope.launch {
            container.provisioner.state.collect { state ->
                _ui.value = _ui.value.copy(
                    provision = state,
                    screen = when {
                        state is ProvisionState.Ready && _ui.value.screen == Screen.Onboarding -> Screen.Projects
                        else -> _ui.value.screen
                    },
                )
                if (state is ProvisionState.Ready && !settings.onboardingDone) {
                    settings.onboardingDone = true
                }
            }
        }
        viewModelScope.launch {
            container.session.state.collect { chat -> _ui.value = _ui.value.copy(chat = chat) }
        }
        viewModelScope.launch {
            container.session.connection.collect { conn -> _ui.value = _ui.value.copy(connection = conn) }
        }
        viewModelScope.launch {
            container.session.notices.collect { notice -> _ui.value = _ui.value.copy(notice = notice.text) }
        }
        viewModelScope.launch {
            container.session.pendingUi.collect { pending -> _ui.value = _ui.value.copy(pendingUi = pending) }
        }
        loadProjects()

        // Provision automatically the first time so the onboarding screen
        // actually advances instead of sitting at "Aguardando".
        autoProvisionIfNeeded()
    }

    /**
     * Starts provisioning unless it is already running or the environment is
     * ready. Safe to call from multiple entry points (init, resume, retry).
     */
    private fun autoProvisionIfNeeded() {
        if (container.provisioner.isProvisioned()) return
        AgentService.startProvisioning(appContext)
        container.ensureProvisioned(force = false)
    }

    // -----------------------------------------------------------------------
    // Provisioning
    // -----------------------------------------------------------------------

    fun startProvisioning(force: Boolean = false) {
        AgentService.startProvisioning(appContext)
        container.ensureProvisioned(force)
    }

    fun clearEnvironment() {
        viewModelScope.launch {
            container.session.stop()
            _ui.value = _ui.value.copy(
                screen = Screen.Onboarding,
                provision = ProvisionState.Idle,
                activeProject = null,
            )
            AgentService.startProvisioning(appContext)
            container.ensureProvisioned(force = true)
        }
    }

    // -----------------------------------------------------------------------
    // Projects
    // -----------------------------------------------------------------------

    fun loadProjects() {
        _ui.value = _ui.value.copy(projects = container.projects.list())
    }

    fun createProject(name: String) {
        runCatching { container.projects.create(name) }
        loadProjects()
    }

    fun cloneProject(url: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(cloning = true, projectsError = null)
            val result = container.projects.clone(url)
            _ui.value = _ui.value.copy(cloning = false, projectsError = result.exceptionOrNull()?.message)
            loadProjects()
        }
    }

    fun openProject(project: Project) {
        _ui.value = _ui.value.copy(activeProject = project, screen = Screen.Chat)
        AgentService.start(appContext)
        viewModelScope.launch {
            container.session.start(
                container.launchConfig().copy(
                    workDirGuest = project.guestPath,
                    sessionName = project.name,
                ),
            )
        }
    }

    fun openSettings() {
        val provisionLog = runCatching {
            File(container.paths.logs, "provision.log").takeIf { it.exists() }?.readText()
        }.getOrNull().orEmpty()
        val stderr = container.session.stderrLog()
        val diagnostics = buildString {
            if (provisionLog.isNotBlank()) {
                appendLine("== provisionamento ==")
                appendLine(provisionLog.takeLast(4000))
            }
            if (stderr.isNotBlank()) {
                appendLine("== agente (stderr) ==")
                appendLine(stderr.takeLast(4000))
            }
        }
        _ui.value = _ui.value.copy(
            screen = Screen.Settings,
            diagnostics = diagnostics,
            providers = container.settings.allProviders(),
        )
    }

    fun backToProjects() {
        _ui.value = _ui.value.copy(screen = Screen.Projects)
    }

    // -----------------------------------------------------------------------
    // Chat
    // -----------------------------------------------------------------------

    fun send(text: String) {
        viewModelScope.launch { container.session.sendPrompt(text) }
    }

    fun steer(text: String) {
        viewModelScope.launch { container.session.steer(text) }
    }

    fun stopAgent() {
        viewModelScope.launch { container.session.abort() }
    }

    fun newSession() {
        viewModelScope.launch { container.session.newSession() }
        AgentService.stop(appContext)
    }

    fun pickModel(provider: String, id: String) {
        container.settings.provider = provider
        container.settings.model = id
        _ui.value = _ui.value.copy(selectedProvider = provider, showModelPicker = false)
        viewModelScope.launch { container.session.setModel(provider, id) }
    }

    fun pickThinking(level: String) {
        container.settings.thinking = level
        _ui.value = _ui.value.copy(thinkingLevel = level, showThinkingPicker = false)
        viewModelScope.launch { container.session.setThinkingLevel(level) }
    }

    fun setPicker(kind: Pickers, open: Boolean) {
        _ui.value = _ui.value.copy(
            showModelPicker = if (kind == Pickers.Model) open else _ui.value.showModelPicker,
            showThinkingPicker = if (kind == Pickers.Thinking) open else _ui.value.showThinkingPicker,
            showAttachSheet = if (kind == Pickers.Attach) open else _ui.value.showAttachSheet,
        )
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun respondExtensionUi(request: ExtensionUiRequest, value: String?, confirmed: Boolean?, cancelled: Boolean) {
        viewModelScope.launch { container.session.respondExtensionUi(request.id, value, confirmed, cancelled) }
    }

    // -----------------------------------------------------------------------
    // Providers & API keys
    // -----------------------------------------------------------------------

    fun selectProvider(providerId: String) {
        container.settings.provider = providerId
        _ui.value = _ui.value.copy(selectedProvider = providerId)
    }

    fun saveApiKey(provider: String, key: String): Boolean {
        return runCatching {
            container.settings.setApiKey(provider, key)
        }.onFailure {
            _ui.value = _ui.value.copy(notice = "Não foi possível salvar a chave: ${it.message}")
        }.isSuccess
    }

    fun addCustomProvider(id: String, label: String, baseUrl: String, api: String, models: String) {
        val safeId = id.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        if (safeId.isBlank() || baseUrl.isBlank()) {
            _ui.value = _ui.value.copy(notice = "Informe um nome e uma URL válidos.")
            return
        }
        val envVar = "HARNESS_KEY_" + safeId.uppercase().replace(Regex("[^A-Z0-9]"), "_")
        container.settings.addCustomProvider(
            CustomProvider(
                id = safeId,
                label = label.ifBlank { safeId },
                baseUrl = baseUrl.trim(),
                api = api,
                envVar = envVar,
                models = models,
            ),
        )
        container.modelCatalog.syncModelsJson()
        _ui.value = _ui.value.copy(
            providers = container.settings.allProviders(),
            selectedProvider = safeId,
        )
        container.settings.provider = safeId
    }

    fun removeCustomProvider(id: String) {
        container.settings.removeCustomProvider(id)
        container.modelCatalog.syncModelsJson()
        _ui.value = _ui.value.copy(
            providers = container.settings.allProviders(),
            selectedProvider = if (_ui.value.selectedProvider == id) "anthropic" else _ui.value.selectedProvider,
        )
    }

    // -----------------------------------------------------------------------
    // "Obter modelos" dialog
    // -----------------------------------------------------------------------

    fun openModelsDialog(providerId: String) {
        _ui.value = _ui.value.copy(
            modelsDialogProvider = providerId,
            modelsLoading = true,
            modelsError = null,
            models = emptyList(),
        )
        viewModelScope.launch {
            val entries = runCatching { fetchModels(providerId) }
            entries.onSuccess { list ->
                val existing = container.settings.selectedModels(providerId)
                val noneSelected = existing.isEmpty()
                _ui.value = _ui.value.copy(
                    modelsLoading = false,
                    models = list.map { (provider, id) ->
                        ModelOption(provider, id, selected = noneSelected || id in existing)
                    },
                )
            }.onFailure { t ->
                _ui.value = _ui.value.copy(
                    modelsLoading = false,
                    modelsError = t.message ?: "Falha ao obter modelos",
                )
            }
        }
    }

    /**
     * Prefers the live RPC process (authoritative for the running Pi), falling
     * back to `pi --list-models` when no session is running.
     */
    private suspend fun fetchModels(providerId: String): List<Pair<String, String>> {
        val fromRpc = runCatching { container.session.listAvailableModels() }.getOrDefault(emptyList())
        val all = if (fromRpc.isNotEmpty()) fromRpc else {
            container.modelCatalog.listModels(container.settings.apiKeyEnv()).map { it.provider to it.id }
        }
        // Show every provider's models but keep the selected one first; the user
        // can pick models across providers.
        return all.distinct()
    }

    fun toggleModel(qualifiedId: String) {
        _ui.value = _ui.value.copy(
            models = _ui.value.models.map {
                if (it.qualifiedId == qualifiedId) it.copy(selected = !it.selected) else it
            },
        )
    }

    /** Models the user kept for a provider (empty when never configured). */
    fun selectedModelsFor(providerId: String): List<String> =
        container.settings.selectedModels(providerId).toList()

    fun selectAllModels(selected: Boolean) {
        _ui.value = _ui.value.copy(models = _ui.value.models.map { it.copy(selected = selected) })
    }

    fun saveModelSelection() {
        // Persist the selection per provider, grouping the checked options.
        _ui.value.models.groupBy { it.provider }.forEach { (providerId, options) ->
            val selected = options.filter { it.selected }.map { it.id }.toSet()
            container.settings.setSelectedModels(providerId, selected)
        }
        val wasProvider = _ui.value.modelsDialogProvider
        _ui.value = _ui.value.copy(modelsDialogProvider = null, models = emptyList())

        // If the current model is no longer in the allowed set, clear it so the
        // user is prompted to pick another one.
        val current = container.settings.model
        if (wasProvider != null && wasProvider == container.settings.provider && current.isNotBlank()) {
            if (!container.settings.selectedModels(wasProvider).contains(current)) {
                container.settings.model = ""
            }
        }
    }

    fun closeModelsDialog() {
        _ui.value = _ui.value.copy(modelsDialogProvider = null, models = emptyList(), modelsError = null)
    }

    override fun onCleared() {
        super.onCleared()
        container.session.stop()
    }

    enum class Pickers { Model, Thinking, Attach }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarnessViewModel(app) }
        }
    }
}
