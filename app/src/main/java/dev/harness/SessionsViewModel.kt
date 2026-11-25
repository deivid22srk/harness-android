package dev.harness

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.harness.core.projects.Project
import dev.harness.core.sessions.RenameResult
import dev.harness.core.sessions.SessionRepository
import dev.harness.core.sessions.SessionSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable UI state of the "Sessões do projeto" screen (UDF — the screen
 * renders this and dispatches intents; zero business logic in composables).
 */
data class SessionsUiState(
    val project: Project? = null,
    val loading: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val query: String = "",
    val activeSessionFile: String? = null,
    val error: String? = null,
    /** One-shot destructive-pending marker: delete waiting for undo window. */
    val pendingUndo: SessionSummary? = null,
) {
    val visibleSessions: List<SessionSummary>
        get() = run {
            val q = query.trim()
            val base = if (q.isEmpty()) {
                sessions
            } else {
                sessions.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        (it.firstUserText?.contains(q, ignoreCase = true) ?: false)
                }
            }
            base.sortedByDescending { it.updatedAt }
        }
}

/**
 * Owns the session list of a project: load, search, rename, delete (with undo
 * window) and duplicate. Listing reads the Pi session files directly (no Pi
 * process needed); mutations on the ACTIVE session go through the chat
 * session (RPC) and are delegated to [HarnessViewModel] by the screen.
 */
class SessionsViewModel(private val app: Application) : ViewModel() {

    private val container = (app as HarnessApp).container
    private val repository = SessionRepository(container.paths.home, container.paths.stateDir)

    private val _ui = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _ui.asStateFlow()

    private var deleteJob: Job? = null

    /** Reloads the list for a project and marks its active session. */
    fun load(project: Project, activeSessionFile: String?) {
        _ui.value = _ui.value.copy(project = project, loading = true, error = null)
        viewModelScope.launch {
            val list = runCatching { repository.listSessions(project) }
                .onFailure {
                    _ui.value = _ui.value.copy(loading = false, error = it.message ?: "Falha ao ler as sessões.")
                }
                .getOrDefault(emptyList())
            _ui.value = _ui.value.copy(
                loading = false,
                sessions = list,
                activeSessionFile = activeSessionFile,
            )
        }
    }

    /** Refreshes keeping the current project (e.g. after switching sessions). */
    fun refresh(activeSessionFile: String?) {
        val project = _ui.value.project ?: return
        load(project, activeSessionFile)
    }

    fun setQuery(query: String) {
        _ui.value = _ui.value.copy(query = query)
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /**
     * Renames a session. The active session is renamed by the Pi itself
     * (RPC set_session_name — see HarnessViewModel.renameActiveSession);
     * closed sessions are renamed by appending a session_info entry.
     */
    fun rename(summary: SessionSummary, newName: String, isActive: Boolean, onActiveRename: (String) -> Unit) {
        viewModelScope.launch {
            if (isActive) {
                onActiveRename(newName)
                // The Pi appends the entry; refresh after a short delay.
                delay(400)
                refresh(_ui.value.activeSessionFile)
            } else {
                val result = repository.renameSession(summary, newName)
                when (result) {
                    is RenameResult.Success -> refresh(_ui.value.activeSessionFile)
                    is RenameResult.Failure -> _ui.value = _ui.value.copy(error = result.message)
                }
            }
        }
    }

    /**
     * Deletes with a 6 s undo window. Deleting the ACTIVE session is blocked
     * with a clear message (switch first) — deleting the file under a live Pi
     * would resurrect a partial file on the next append.
     */
    fun delete(summary: SessionSummary, isActive: Boolean) {
        if (isActive) {
            _ui.value = _ui.value.copy(error = "Troque de sessão antes de excluir a sessão ativa.")
            return
        }
        deleteJob?.cancel()
        _ui.value = _ui.value.copy(
            sessions = _ui.value.sessions.filterNot { it.file == summary.file },
            pendingUndo = summary,
        )
        deleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            val removed = repository.deleteSession(summary)
            _ui.value = _ui.value.copy(pendingUndo = null)
            if (!removed) refresh(_ui.value.activeSessionFile)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        val summary = _ui.value.pendingUndo ?: return
        _ui.value = _ui.value.copy(
            pendingUndo = null,
            sessions = (_ui.value.sessions + summary).sortedByDescending { it.updatedAt },
        )
    }

    /** Guest-path of a host session file, for switch_session RPC calls. */
    fun guestPathOf(hostPath: String): String = repository.hostToGuestPath(hostPath)

    /** Host path of a guest session file (get_state.sessionFile → list marker). */
    fun guestToHost(guestPath: String): String = runCatching {
        repository.guestToHostPath(guestPath)
    }.getOrDefault(guestPath)

    /** Snackbar text for the undo window of a deleted session. */
    fun undoMessage(summary: SessionSummary): String =
        app.getString(R.string.sessions_deleted_snackbar, summary.title)

    /** Snackbar action label for the undo window. */
    fun undoLabel(): String = app.getString(R.string.sessions_undo)

    /** Marker of the last active session per project (instant resume). */
    fun lastSessionFileOf(project: Project): String? = repository.lastSessionFileFor(project)

    /** Newest session file of a project — first resume attempt fallback. */
    fun newestSessionFileOf(project: Project): String? = repository.newestSessionFile(project)

    fun setLastSessionFile(project: Project, hostSessionFile: String?) =
        repository.setLastSessionFile(project, hostSessionFile)

    override fun onCleared() {
        deleteJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val UNDO_WINDOW_MS = 6_000L

        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionsViewModel(app) }
        }
    }
}
