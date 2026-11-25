package dev.harness

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.harness.core.env.ProvisionState
import dev.harness.core.pi.ConnectionState
import dev.harness.core.pi.ImageAttachment
import dev.harness.core.projects.Project
import dev.harness.core.rpc.ExtensionUiRequest
import dev.harness.core.settings.CustomProvider
import dev.harness.core.settings.Provider
import dev.harness.core.state.ChatState
import dev.harness.core.state.NoticeLevel
import dev.harness.service.AgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** One-shot app-level notification (rendered as a snackbar). */
data class UiNotice(
    val text: String,
    val level: NoticeLevel = NoticeLevel.Info,
    /** Non-null renders an action button; [action] is one of the constants below. */
    val action: String? = null,
) {
    companion object {
        const val ACTION_UNDO = "undo"
    }
}

/** Attachment queued in the composer before sending. */
data class PendingImage(
    val attachment: ImageAttachment,
    val label: String,
)

data class HarnessUiState(
    val screen: Screen = Screen.Onboarding,
    val provision: ProvisionState = ProvisionState.Idle,
    val connection: ConnectionState = ConnectionState.Stopped,
    val chat: ChatState = ChatState(),
    val projects: List<Project> = emptyList(),
    val projectsQuery: String = "",
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
    val notice: UiNotice? = null,
    val pendingUi: List<ExtensionUiRequest> = emptyList(),
    // Composer attachments
    val pendingImages: List<PendingImage> = emptyList(),
    // "Editar e reenviar": prefill pushed into the composer (token forces the effect)
    val composerPrefill: String? = null,
    val composerPrefillToken: Int = 0,
    // Chat model list for the composer picker (already filtered by selection)
    val chatModels: List<Pair<String, String>> = emptyList(),
    // Appearance
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
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

    /** Last prompt sent in the current session — powers "regenerar". */
    private var lastPrompt: String? = null

    /** Images of the last prompt — regenerating must not lose attachments. */
    private var lastImages: List<ImageAttachment> = emptyList()

    /** Pending project delete job (cancelled by "Desfazer"). */
    private var pendingDeleteJob: Job? = null

    /** Launch config of the active session, for restarts. */
    private var activeLaunch: dev.harness.core.pi.PiLaunchConfig? = null

    init {
        val settings = container.settings
        val alreadyProvisioned = container.provisioner.isProvisioned()
        _ui.value = _ui.value.copy(
            providers = settings.allProviders(),
            selectedProvider = settings.provider,
            thinkingLevel = settings.thinking,
            provision = container.provisioner.state.value,
            screen = if (alreadyProvisioned) Screen.Projects else Screen.Onboarding,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            fontScale = settings.fontScale,
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
            container.session.state.collect { chat ->
                // Skip recomposition when nothing visibly changed (streaming
                // publishes ~30 fps; equal states must not emit).
                if (_ui.value.chat != chat) {
                    _ui.value = _ui.value.copy(chat = chat)
                    refreshChatModels(chat)
                }
            }
        }
        viewModelScope.launch {
            container.session.connection.collect { conn ->
                _ui.value = _ui.value.copy(connection = conn)
            }
        }
        viewModelScope.launch {
            container.session.notices.collect { notice ->
                setNotice(UiNotice(notice.text, notice.level))
            }
        }
        viewModelScope.launch {
            container.session.pendingUi.collect { pending -> _ui.value = _ui.value.copy(pendingUi = pending) }
        }
        loadProjects()
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

    private fun setNotice(notice: UiNotice?) {
        _ui.value = _ui.value.copy(notice = notice)
    }

    fun dismissNotice() {
        if (_ui.value.notice?.action == UiNotice.ACTION_UNDO) {
            pendingDeleteJob?.cancel()
            pendingDeleteJob = null
        }
        setNotice(null)
    }

    fun onNoticeAction() {
        val notice = _ui.value.notice ?: return
        when (notice.action) {
            UiNotice.ACTION_UNDO -> {
                pendingDeleteJob?.cancel()
                pendingDeleteJob = null
                loadProjects()
            }
        }
        setNotice(null)
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

    fun setProjectsQuery(query: String) {
        _ui.value = _ui.value.copy(projectsQuery = query)
    }

    /** Visible projects after the search filter (UI-side filtering is fine at this scale). */
    fun visibleProjects(): List<Project> {
        val query = _ui.value.projectsQuery.trim()
        val projects = _ui.value.projects
        if (query.isEmpty()) return projects
        return projects.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun loadProjects() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { container.projects.list() }
            _ui.value = _ui.value.copy(projects = list)
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { container.projects.create(name) }
            }
            result.onSuccess {
                loadProjects()
                setNotice(UiNotice(appContext.getString(R.string.notice_project_created, it.name)))
            }.onFailure {
                setNotice(
                    UiNotice(
                        appContext.getString(R.string.notice_project_create_failed, it.message ?: ""),
                        NoticeLevel.Error,
                    ),
                )
            }
        }
    }

    fun cloneProject(url: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(cloning = true, projectsError = null)
            val result = container.projects.clone(url)
            _ui.value = _ui.value.copy(cloning = false)
            result.onSuccess {
                loadProjects()
                setNotice(UiNotice(appContext.getString(R.string.notice_project_cloned, it.name)))
            }.onFailure {
                setNotice(
                    UiNotice(
                        appContext.getString(R.string.notice_project_clone_failed),
                        NoticeLevel.Error,
                    ),
                )
            }
        }
    }

    /**
     * Deletes a project with a 6 s undo window (the snackbar offers "Desfazer").
     * Actual deletion only happens after the window closes.
     */
    fun deleteProject(project: Project) {
        pendingDeleteJob?.cancel()
        _ui.value = _ui.value.copy(projects = _ui.value.projects.filterNot { it.hostPath == project.hostPath })
        setNotice(
            UiNotice(
                appContext.getString(R.string.notice_project_deleted, project.name),
                action = UiNotice.ACTION_UNDO,
            ),
        )
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            withContext(Dispatchers.IO) { container.projects.delete(project) }
            pendingDeleteJob = null
        }
    }

    fun openProject(project: Project) {
        // Returning to the project that is already running must NOT relaunch
        // the agent: just switch the screen and keep the live session.
        val resuming = _ui.value.activeProject?.hostPath == project.hostPath &&
            _ui.value.connection.let { it == ConnectionState.Connected || it == ConnectionState.Starting } &&
            _ui.value.chat.items.isNotEmpty()
        _ui.value = _ui.value.copy(activeProject = project, screen = Screen.Chat)
        if (resuming) return

        AgentService.start(appContext)
        viewModelScope.launch {
            val config = container.launchConfig().copy(
                workDirGuest = project.guestPath,
                sessionName = project.name,
            )
            activeLaunch = config
            container.session.start(config)
        }
    }

    /** Restarts the agent after a connection failure. */
    fun restartSession() {
        val config = activeLaunch ?: run {
            _ui.value.activeProject?.let { openProject(it) }
            return
        }
        viewModelScope.launch {
            container.session.start(config)
        }
    }

    fun openSettings() {
        viewModelScope.launch {
            val diagnostics = withContext(Dispatchers.IO) { buildDiagnostics() }
            _ui.value = _ui.value.copy(
                screen = Screen.Settings,
                diagnostics = diagnostics,
                providers = container.settings.allProviders(),
            )
        }
    }

    private fun buildDiagnostics(): String {
        val provisionLog = runCatching {
            File(container.paths.logs, "provision.log").takeIf { it.exists() }?.readText()
        }.getOrNull().orEmpty()
        val stderr = container.session.stderrLog()
        return buildString {
            if (provisionLog.isNotBlank()) {
                appendLine("== provisionamento ==")
                appendLine(provisionLog.takeLast(4000))
            }
            if (stderr.isNotBlank()) {
                appendLine("== agente (stderr) ==")
                appendLine(stderr.takeLast(4000))
            }
        }
    }

    fun backToProjects() {
        _ui.value = _ui.value.copy(screen = Screen.Projects)
        loadProjects()
    }

    // -----------------------------------------------------------------------
    // Chat
    // -----------------------------------------------------------------------

    fun send(text: String) {
        val images = _ui.value.pendingImages.map { it.attachment }
        viewModelScope.launch {
            container.session.sendPrompt(text, images)
        }
        lastPrompt = text
        lastImages = images
        _ui.value = _ui.value.copy(pendingImages = emptyList(), composerPrefill = null)
    }

    fun steer(text: String) {
        viewModelScope.launch { container.session.steer(text) }
    }

    /** Prefills the composer with a past message so the user can edit it. */
    fun requestEditResend(text: String) {
        _ui.value = _ui.value.copy(
            composerPrefill = text,
            composerPrefillToken = _ui.value.composerPrefillToken + 1,
        )
    }

    fun stopAgent() {
        viewModelScope.launch { container.session.abort() }
    }

    /** Re-sends the last user prompt with its attachments (Regenerate). */
    fun regenerate() {
        val prompt = lastPrompt ?: return
        if (_ui.value.chat.isBusy) return
        viewModelScope.launch { container.session.sendPrompt(prompt, lastImages) }
    }

    /** Last prompt for the UI to decide whether "Regenerar" is offered. */
    fun lastPromptForRegeneration(): String? =
        lastPrompt?.takeIf { !_ui.value.chat.isBusy }

    fun newSession() {
        viewModelScope.launch {
            container.session.newSession()
            lastPrompt = null
            lastImages = emptyList()
        }
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
        if (kind == Pickers.Model && open) refreshChatModels(_ui.value.chat)
        _ui.value = _ui.value.copy(
            showModelPicker = if (kind == Pickers.Model) open else _ui.value.showModelPicker,
            showThinkingPicker = if (kind == Pickers.Thinking) open else _ui.value.showThinkingPicker,
            showAttachSheet = if (kind == Pickers.Attach) open else _ui.value.showAttachSheet,
        )
    }

    /** Models offered in the composer picker: user selection or provider default. */
    private fun refreshChatModels(chat: ChatState) {
        val providerId = _ui.value.selectedProvider
        val provider = _ui.value.providers.firstOrNull { it.id == providerId }
        val selected = runCatching { container.settings.selectedModels(providerId) }.getOrDefault(emptySet())
        val models = when {
            selected.isNotEmpty() -> selected.map { providerId to it }
            provider?.defaultModel != null -> listOf(providerId to provider.defaultModel!!)
            chat.model != null -> listOf(chat.model.provider to chat.model.id)
            else -> emptyList()
        }
        if (_ui.value.chatModels != models) {
            _ui.value = _ui.value.copy(chatModels = models)
        }
    }

    fun respondExtensionUi(request: ExtensionUiRequest, value: String?, confirmed: Boolean?, cancelled: Boolean) {
        viewModelScope.launch { container.session.respondExtensionUi(request.id, value, confirmed, cancelled) }
    }

    // -----------------------------------------------------------------------
    // Attachments (photo picker -> base64 -> Pi)
    // -----------------------------------------------------------------------

    fun addImageAttachment(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = appContext.contentResolver
                val mime = resolver.getType(uri) ?: "image/png"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Não foi possível ler a imagem")
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val label = appContext.getString(R.string.attach_image_label, bytes.size / 1024)
                PendingImage(ImageAttachment(base64, mime), label)
            }.onSuccess { image ->
                _ui.value = _ui.value.copy(pendingImages = _ui.value.pendingImages + image)
            }.onFailure {
                setNotice(
                    UiNotice(appContext.getString(R.string.notice_attach_failed), NoticeLevel.Error),
                )
            }
        }
    }

    fun removePendingImage(image: PendingImage) {
        _ui.value = _ui.value.copy(pendingImages = _ui.value.pendingImages - image)
    }

    // -----------------------------------------------------------------------
    // Appearance
    // -----------------------------------------------------------------------

    fun setThemeMode(mode: String) {
        container.settings.themeMode = mode
        _ui.value = _ui.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        container.settings.dynamicColor = enabled
        _ui.value = _ui.value.copy(dynamicColor = enabled)
    }

    fun setFontScale(scale: Float) {
        container.settings.fontScale = scale
        _ui.value = _ui.value.copy(fontScale = scale)
    }

    // -----------------------------------------------------------------------
    // Providers & API keys
    // -----------------------------------------------------------------------

    fun selectProvider(providerId: String) {
        container.settings.provider = providerId
        _ui.value = _ui.value.copy(selectedProvider = providerId)
        refreshChatModels(_ui.value.chat)
    }

    fun saveApiKey(provider: String, key: String): Boolean {
        return runCatching {
            container.settings.setApiKey(provider, key)
        }.onFailure {
            setNotice(
                UiNotice(
                    appContext.getString(R.string.notice_key_save_failed, it.message ?: ""),
                    NoticeLevel.Error,
                ),
            )
        }.isSuccess
    }

    fun addCustomProvider(id: String, label: String, baseUrl: String, api: String, models: String) {
        val safeId = id.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        if (safeId.isBlank() || baseUrl.isBlank()) {
            setNotice(UiNotice(appContext.getString(R.string.notice_provider_invalid), NoticeLevel.Error))
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
        refreshChatModels(_ui.value.chat)
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
                    modelsError = t.message ?: appContext.getString(R.string.models_fetch_failed),
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
        return all.distinct()
    }

    fun toggleModel(qualifiedId: String) {
        _ui.value = _ui.value.copy(
            models = _ui.value.models.map {
                if (it.qualifiedId == qualifiedId) it.copy(selected = !it.selected) else it
            },
        )
    }

    fun selectAllModels(selected: Boolean) {
        _ui.value = _ui.value.copy(models = _ui.value.models.map { it.copy(selected = selected) })
    }

    fun saveModelSelection() {
        _ui.value.models.groupBy { it.provider }.forEach { (providerId, options) ->
            val selected = options.filter { it.selected }.map { it.id }.toSet()
            container.settings.setSelectedModels(providerId, selected)
        }
        val wasProvider = _ui.value.modelsDialogProvider
        _ui.value = _ui.value.copy(modelsDialogProvider = null, models = emptyList())

        val current = container.settings.model
        if (wasProvider != null && wasProvider == container.settings.provider && current.isNotBlank()) {
            if (!container.settings.selectedModels(wasProvider).contains(current)) {
                container.settings.model = ""
            }
        }
        refreshChatModels(_ui.value.chat)
    }

    fun closeModelsDialog() {
        _ui.value = _ui.value.copy(modelsDialogProvider = null, models = emptyList(), modelsError = null)
    }

    override fun onCleared() {
        super.onCleared()
        // The session lives in the application-scoped container and keeps
        // running under the foreground service; an explicit stop happens only
        // via clearEnvironment/newSession. This keeps conversations alive
        // across Activity recreation.
        pendingDeleteJob?.cancel()
    }

    enum class Pickers { Model, Thinking, Attach }

    companion object {
        private const val UNDO_WINDOW_MS = 6_000L

        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarnessViewModel(app) }
        }
    }
}
