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
import dev.harness.core.state.ChatState
import dev.harness.service.AgentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { Onboarding, Projects, Chat, Settings }

data class HarnessUiState(
    val screen: Screen = Screen.Onboarding,
    val provision: ProvisionState = ProvisionState.Idle,
    val connection: ConnectionState = ConnectionState.Stopped,
    val chat: ChatState = ChatState(),
    val projects: List<Project> = emptyList(),
    val cloning: Boolean = false,
    val projectsError: String? = null,
    val activeProject: Project? = null,
    val selectedProvider: String = "anthropic",
    val thinkingLevel: String = "medium",
    val showModelPicker: Boolean = false,
    val showThinkingPicker: Boolean = false,
    val showAttachSheet: Boolean = false,
    val diagnostics: String = "",
    val notice: String? = null,
    val pendingUi: List<ExtensionUiRequest> = emptyList(),
)

class HarnessViewModel(private val app: Application) : ViewModel() {

    private val container = (app as HarnessApp).container
    private val appContext: Context get() = app.applicationContext

    private val _ui = MutableStateFlow(HarnessUiState())
    val uiState: StateFlow<HarnessUiState> = _ui.asStateFlow()

    init {
        val settings = container.settings
        _ui.value = _ui.value.copy(
            selectedProvider = settings.provider,
            thinkingLevel = settings.thinking,
            provision = container.provisioner.state.value,
            screen = if (container.provisioner.isProvisioned()) Screen.Projects else Screen.Onboarding,
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
    }

    // -----------------------------------------------------------------------
    // Provisioning
    // -----------------------------------------------------------------------

    fun startProvisioning(force: Boolean = false) {
        viewModelScope.launch { container.provisioner.provision(force) }
    }

    fun clearEnvironment() {
        viewModelScope.launch {
            container.session.stop()
            _ui.value = _ui.value.copy(
                screen = Screen.Onboarding,
                provision = ProvisionState.Idle,
                activeProject = null,
            )
            container.provisioner.provision(force = true)
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
        _ui.value = _ui.value.copy(screen = Screen.Settings, diagnostics = container.session.stderrLog())
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

    fun saveApiKey(provider: String, key: String) {
        container.settings.setApiKey(provider, key)
        _ui.value = _ui.value.copy(selectedProvider = provider)
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun respondExtensionUi(request: ExtensionUiRequest, value: String?, confirmed: Boolean?, cancelled: Boolean) {
        viewModelScope.launch { container.session.respondExtensionUi(request.id, value, confirmed, cancelled) }
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
