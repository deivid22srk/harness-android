package dev.harness

import android.app.Application
import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProotCommand
import dev.harness.core.env.Provisioner
import dev.harness.core.pi.AgentSession
import dev.harness.core.projects.ProjectRepository
import dev.harness.core.settings.SettingsRepository
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

    init {
        paths.ensureDirs()
    }

    /** Resolves the currently configured model, falling back to the catalog. */
    fun launchConfig() = dev.harness.core.pi.PiLaunchConfig(
        provider = settings.provider.ifBlank { null },
        model = settings.model.ifBlank { null },
        thinking = settings.thinking.ifBlank { null },
        apiKeys = settings.apiKeyEnv(),
    )
}

class HarnessApp : Application() {
    lateinit var container: HarnessContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = HarnessContainer(this)
    }
}
