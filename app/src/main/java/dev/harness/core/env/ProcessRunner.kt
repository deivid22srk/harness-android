package dev.harness.core.env

import java.io.File

/** Thin wrapper over [ProcessBuilder] with PRoot-friendly defaults. */
object ProcessRunner {

    class Running(
        val process: Process,
        val argv: List<String>,
    ) {
        fun destroy() {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun start(
        argv: List<String>,
        env: Map<String, String>,
        workDir: File = File("/"),
        mergeStderr: Boolean = false,
    ): Running {
        val builder = ProcessBuilder(argv)
        builder.directory(workDir)
        if (mergeStderr) builder.redirectErrorStream(true)
        val pbEnv = builder.environment()
        pbEnv.clear()
        pbEnv.putAll(env)
        val process = try {
            builder.start()
        } catch (t: Throwable) {
            throw java.io.IOException("Failed to start ${argv.firstOrNull()}: ${t.message}", t)
        }
        return Running(process, argv)
    }

    /** Run to completion, capturing combined output. Used by provisioning steps. */
    fun runCombined(
        argv: List<String>,
        env: Map<String, String>,
        workDir: File = File("/"),
        onLine: ((String) -> Unit)? = null,
        onStarted: ((Process) -> Unit)? = null,
    ): Result {
        val running = start(argv, env, workDir, mergeStderr = true)
        onStarted?.invoke(running.process)
        val output = StringBuilder()
        running.process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.append(line).append('\n')
                onLine?.invoke(line)
            }
        }
        val exit = running.process.waitFor()
        return Result(exit, output.toString())
    }

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
        fun tail(lines: Int = 40): String =
            output.trim().lines().takeLast(lines).joinToString("\n")
    }
}
