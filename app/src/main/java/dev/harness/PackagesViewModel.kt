package dev.harness

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.harness.core.pi.InstalledPackage
import dev.harness.core.pi.PackageOperation
import dev.harness.core.pi.PackageSearchResult
import dev.harness.core.pi.PiPackageManager
import dev.harness.core.projects.Project
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Explorador (busca npm), paginado (from acumulado, como no arquivo do usuário). */
data class PackageSearchState(
    val query: String = "",
    val results: List<PackageSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val nextFrom: Int = 0,
)

/**
 * Immutable UI state of the "Pacotes do Pi" screen (UDF). The Pi remains the
 * source of truth: this state only mirrors what the settings files say plus
 * the live operation log.
 */
data class PackagesUiState(
    val loading: Boolean = true,
    val project: Project? = null,
    val global: List<InstalledPackage> = emptyList(),
    val projectPackages: List<InstalledPackage> = emptyList(),
    val search: PackageSearchState = PackageSearchState(),
    val operation: PackageOperation? = null,
    /** One-shot human error + technical detail for "Copiar diagnóstico". */
    val error: String? = null,
    val errorDetail: String? = null,
    /** True after install/remove/update succeeded — offer session reload. */
    val needsReload: Boolean = false,
    /** Security confirmation pending (dialog is open on the screen). */
    val pendingInstall: PendingInstall? = null,
)

/** Install intent awaiting the security confirmation (§5.3.2 obrigatório). */
data class PendingInstall(
    val source: String,
    val scopeIsProject: Boolean,
)

/**
 * Owns the "Pacotes do Pi" screen. Blocks operations while the agent is
 * generating, serializes them through [PiPackageManager.opMutex] and surfaces
 * the reload hint (session restart) after every successful mutation.
 */
class PackagesViewModel(private val app: Application) : ViewModel() {

    private val container = (app as HarnessApp).container
    private val manager = PiPackageManager(container.paths, container.proot)

    private val _ui = MutableStateFlow(PackagesUiState())
    val uiState: StateFlow<PackagesUiState> = _ui.asStateFlow()

    private var searchJob: Job? = null
    private var opJob: Job? = null

    fun load(project: Project?) {
        _ui.value = _ui.value.copy(loading = true, project = project, error = null, errorDetail = null)
        viewModelScope.launch {
            val installed = runCatching { manager.listInstalled(project?.hostPath) }
                .onFailure {
                    _ui.value = _ui.value.copy(loading = false, error = it.message ?: "Falha ao ler os pacotes.")
                }
                .getOrDefault(emptyList())
            _ui.value = _ui.value.copy(
                loading = false,
                global = installed.filter { it.scope == dev.harness.core.pi.PackageScope.GLOBAL },
                projectPackages = installed.filter { it.scope == dev.harness.core.pi.PackageScope.PROJECT },
            )
        }
    }

    fun refresh() = load(_ui.value.project)

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null, errorDetail = null)
    }

    fun clearReloadHint() {
        _ui.value = _ui.value.copy(needsReload = false)
    }

    // ------------------------------------------------------------------
    // Install / remove / update
    // ------------------------------------------------------------------

    /** Validates and queues the security confirmation for an install. */
    fun requestInstall(source: String, scopeIsProject: Boolean): Boolean {
        val trimmed = source.trim()
        if (_ui.value.operation?.running == true) {
            _ui.value = _ui.value.copy(error = "Uma operação já está em andamento; aguarde a conclusão.")
            return false
        }
        val problem = manager.validateSource(trimmed)
        if (problem != null) {
            _ui.value = _ui.value.copy(error = problem)
            return false
        }
        if (container.session.state.value.isBusy) {
            _ui.value = _ui.value.copy(error = "O agente está gerando uma resposta. Interrompa antes de alterar pacotes.")
            return false
        }
        _ui.value = _ui.value.copy(pendingInstall = PendingInstall(trimmed, scopeIsProject))
        return true
    }

    /** Called by the security dialog's confirm button (§5.3.2). */
    fun confirmPendingInstall(onDone: () -> Unit) {
        val pending = _ui.value.pendingInstall ?: return
        _ui.value = _ui.value.copy(pendingInstall = null)
        startOperation(
            kind = PackageOperation.OpKind.INSTALL,
            flow = manager.install(
                pending.source,
                local = pending.scopeIsProject,
                projectGuestPath = _ui.value.project?.guestPath,
            ),
            onDone = onDone,
        )
    }

    fun cancelPendingInstall() {
        _ui.value = _ui.value.copy(pendingInstall = null)
    }

    fun removePackage(pkg: InstalledPackage, onDone: () -> Unit) {
        if (_ui.value.operation?.running == true) {
            _ui.value = _ui.value.copy(error = "Uma operação já está em andamento; aguarde a conclusão.")
            return
        }
        if (container.session.state.value.isBusy) {
            _ui.value = _ui.value.copy(error = "O agente está gerando uma resposta. Interrompa antes de alterar pacotes.")
            return
        }
        startOperation(
            kind = PackageOperation.OpKind.REMOVE,
            flow = manager.remove(
                pkg.source,
                local = pkg.scope == dev.harness.core.pi.PackageScope.PROJECT,
                projectGuestPath = _ui.value.project?.guestPath,
            ),
            onDone = onDone,
        )
    }

    fun updateAll(onDone: () -> Unit) {
        if (_ui.value.operation?.running == true) return
        startOperation(
            kind = PackageOperation.OpKind.UPDATE,
            flow = manager.update(null, projectGuestPath = _ui.value.project?.guestPath),
            onDone = onDone,
        )
    }

    /**
     * Atualiza o próprio Pi (`pi update`) — chamado SOMENTE pela categoria
     * Ambiente com confirmação explícita do usuário.
     */
    fun updateSelf(onDone: () -> Unit) {
        if (_ui.value.operation?.running == true) return
        startOperation(
            kind = PackageOperation.OpKind.UPDATE,
            flow = manager.updateSelf(),
            onDone = onDone,
        )
    }

    fun updatePackage(pkg: InstalledPackage, onDone: () -> Unit) {
        if (_ui.value.operation?.running == true) return
        if (pkg.pinned) {
            _ui.value = _ui.value.copy(error = "\"${pkg.source}\" está fixado em uma versão — atualizar não muda o alvo.")
            return
        }
        startOperation(
            kind = PackageOperation.OpKind.UPDATE,
            flow = manager.update(pkg.source, projectGuestPath = _ui.value.project?.guestPath),
            onDone = onDone,
        )
    }

    private fun startOperation(
        kind: PackageOperation.OpKind,
        flow: kotlinx.coroutines.flow.Flow<String>,
        onDone: () -> Unit,
    ) {
        opJob?.cancel()
        val collected = ArrayList<String>()
        _ui.value = _ui.value.copy(
            error = null,
            errorDetail = null,
            needsReload = false,
            operation = PackageOperation(kind, target = "", log = emptyList(), running = true),
        )
        opJob = flow
            .onEach { line ->
                collected.add(line)
                _ui.value = _ui.value.copy(
                    operation = (_ui.value.operation ?: PackageOperation(kind, "", emptyList(), true))
                        .copy(log = collected.toList(), running = true),
                )
            }
            .catch { t ->
                _ui.value = _ui.value.copy(
                    operation = (_ui.value.operation)?.copy(running = false, exitCode = -1),
                    error = "A operação falhou: ${t.message ?: "erro desconhecido"}",
                    errorDetail = collected.joinToString("\n"),
                )
                refresh()
                onDone()
            }
            .onCompletion { cause ->
                if (cause == null) {
                    // The manager published the final state (with exit code)
                    // before the flow completed — surface it exactly once.
                    val op = manager.operation.value
                    _ui.value = _ui.value.copy(operation = op)
                    if (op != null && op.exitCode == 0) {
                        _ui.value = _ui.value.copy(needsReload = true)
                    } else {
                        _ui.value = _ui.value.copy(
                            error = "A operação terminou com erro (código ${op?.exitCode ?: -1}). Veja o log abaixo.",
                            errorDetail = op?.log?.joinToString("\n") ?: collected.joinToString("\n"),
                        )
                    }
                    refresh()
                    // Per-package summary after batch operations (§ round-04 P2).
                    viewModelScope.launch {
                        val total = manager.listInstalled(_ui.value.project?.hostPath).size
                        _ui.value = _ui.value.copy(
                            operation = _ui.value.operation?.copy(
                                log = _ui.value.operation?.log.orEmpty() +
                                    listOf("Estado declarado: $total pacote(s)"),
                            ),
                        )
                    }
                    onDone()
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelOperation() {
        // Kill the CLI process first: the blocked runCombined returns and the
        // mutex is released before the next operation starts.
        manager.cancelCurrent()
        opJob?.cancel()
        opJob = null
        _ui.value = _ui.value.copy(
            operation = _ui.value.operation?.copy(running = false, exitCode = -1),
            error = "Operação cancelada. O estado declarado está sendo revalidado…",
        )
        refresh()
        // Explicit post-cancel report (round-04 P2.3): state after revalidate.
        viewModelScope.launch {
            val total = manager.listInstalled(_ui.value.project?.hostPath).size
            _ui.value = _ui.value.copy(
                error = "Operação cancelada. Estado revalidado: $total pacote(s) declarado(s).",
            )
        }
    }

    // ------------------------------------------------------------------
    // Explorer (npm search)
    // ------------------------------------------------------------------

    fun setSearchQuery(query: String) {
        _ui.value = _ui.value.copy(search = _ui.value.search.copy(query = query))
    }

    /** Fresh search (resets pagination). */
    fun search(query: String = _ui.value.search.query) {
        searchJob?.cancel()
        _ui.value = _ui.value.copy(
            search = _ui.value.search.copy(query = query, loading = true, error = null, results = emptyList()),
        )
        runSearch(from = 0, accumulate = false, query = query)
    }

    /** "Carregar mais": acumula a página seguinte. */
    fun loadMore() {
        val current = _ui.value.search
        if (current.loading || !current.hasMore) return
        runSearch(from = current.nextFrom, accumulate = true, query = current.query)
    }

    private fun runSearch(from: Int, accumulate: Boolean, query: String) {
        _ui.value = _ui.value.copy(search = _ui.value.search.copy(loading = true, error = null))
        searchJob = viewModelScope.launch {
            runCatching { manager.searchRegistry(query, from = from) }
                .onSuccess { page ->
                    val current = _ui.value.search
                    _ui.value = _ui.value.copy(
                        search = current.copy(
                            loading = false,
                            results = if (accumulate) current.results + page else page,
                            hasMore = page.size >= 60,
                            nextFrom = from + page.size,
                        ),
                    )
                }
                .onFailure { t ->
                    _ui.value = _ui.value.copy(
                        search = _ui.value.search.copy(
                            loading = false,
                            error = "A busca no npm falhou: ${t.message ?: "sem conexão"}.",
                        ),
                    )
                }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        opJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { PackagesViewModel(app) }
        }
    }
}
