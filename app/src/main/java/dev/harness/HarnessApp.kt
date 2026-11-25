package dev.harness

import android.app.Application
import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProotCommand
import dev.harness.core.env.Provisioner
import dev.harness.core.pi.AgentSession
import dev.harness.core.projects.ProjectRepository
import dev.harness.core.settings.SettingsRepository
import dev.harness.core.settings.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container (no Hilt/Dagger, per the "minimal dependencies"
 * principle). Everything is process-scoped and cheap to construct.
 */
class HarnessContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val paths = HarnessPaths(app)
    val proot = ProotCommand(paths)
    val provisioner = Provisioner(paths, proot)
    val settings = SettingsRepository(app)
    val projects = ProjectRepository(paths, proot)
    val session = AgentSession(paths, proot, applicationScope)
    val modelCatalog = ModelCatalog(paths, proot, settings)

    private val provisionLock = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        paths.ensureDirs()
    }

    /**
     * Starts provisioning exactly once, in the application scope so it survives
     * Activity recreation. Subsequent calls are no-ops while it runs.
     */
    fun ensureProvisioned(force: Boolean = false) {
        if (provisioner.isProvisioned() && !force) return
        if (!provisionLock.compareAndSet(false, true)) return
        applicationScope.launch {
            try {
                provisioner.provision(force)
            } finally {
                provisionLock.set(false)
            }
        }
    }

    /** Resolves the currently configured model, falling back to the catalog. */
    fun launchConfig(): dev.harness.core.pi.PiLaunchConfig {
        // Ensure custom providers are visible to Pi before it starts.
        runCatching { modelCatalog.syncModelsJson() }
        return dev.harness.core.pi.PiLaunchConfig(
            provider = settings.provider.ifBlank { null },
            model = settings.model.ifBlank { null },
            thinking = settings.thinking.ifBlank { null },
            modelScope = modelCatalog.modelsScopeArg(settings.provider),
            apiKeys = settings.apiKeyEnv(),
        )
    }
}

class HarnessApp : Application() {
    lateinit var container: HarnessContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = HarnessContainer(this)
    }
}
