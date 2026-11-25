package dev.harness.core.pi

import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProcessRunner
import dev.harness.core.env.ProotCommand
import java.io.File

/** Everything needed to launch one Pi RPC process. */
data class PiLaunchConfig(
    val workDirGuest: String = "/root",
    val provider: String? = null,
    val model: String? = null,
    val thinking: String? = null,
    val sessionName: String? = null,
    val useSession: Boolean = true,
    /** Extra args for `--models` scope (see ModelCatalog.modelsScopeArg). */
    val modelScope: List<String> = emptyList(),
    val extraArgs: List<String> = emptyList(),
    val apiKeys: Map<String, String> = emptyMap(),
)

/**
 * Owns the lifetime of a `pi --mode rpc` process running inside PRoot.
 *
 * stderr is kept separate from stdout so diagnostics never pollute the JSONL
 * protocol stream (see rpc.md "Framing").
 */
class PiProcess(
    private val paths: HarnessPaths,
    private val proot: ProotCommand,
) : PiProcessHandle {
    private var running: ProcessRunner.Running? = null

    /** The underlying OS process, or null when stopped. */
    override val process: Process? get() = running?.process

    override val isAlive: Boolean get() = process?.isAlive == true

    @Synchronized
    override fun start(config: PiLaunchConfig) {
        stop()
        val argv = buildArgv(config)
        val env = proot.hostEnv(config.apiKeys)
        val proc = ProcessRunner.start(argv, env, workDir = paths.filesDir, mergeStderr = false)
        running = proc
    }

    @Synchronized
    override fun stop() {
        running?.let { run ->
            runCatching { run.process.outputStream.close() } // stdin EOF => orderly shutdown
            runCatching { run.process.destroy() }
            if (run.process.isAlive) runCatching { run.process.destroyForcibly() }
        }
        running = null
    }

    private fun buildArgv(config: PiLaunchConfig): List<String> {
        val piArgs = mutableListOf("pi", "--mode", "rpc")
        if (!config.useSession) piArgs += "--no-session"
        config.provider?.let { piArgs += listOf("--provider", it) }
        config.model?.let { piArgs += listOf("--model", it) }
        config.thinking?.let { piArgs += listOf("--thinking", it) }
        config.sessionName?.let { piArgs += listOf("--name", it) }
        piArgs += config.modelScope
        piArgs += config.extraArgs

        val guestEnv = HashMap<String, String>()
        guestEnv.putAll(config.apiKeys)
        return proot.argv(piArgs, guestEnv, workDir = config.workDirGuest)
    }

    fun stderrFile(): File = File(paths.logs, "pi-stderr.log").apply { parentFile?.mkdirs() }
}
