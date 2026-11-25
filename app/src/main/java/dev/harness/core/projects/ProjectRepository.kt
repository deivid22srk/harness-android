package dev.harness.core.projects

import dev.harness.core.env.ProcessRunner
import dev.harness.core.env.ProotCommand
import dev.harness.core.env.HarnessPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Project(
    val name: String,
    val guestPath: String,
    val hostPath: String,
    val isGitRepo: Boolean,
    val lastModified: Long,
)

/**
 * Projects live under `files/home/projects`, which is bind-mounted to `/root`
 * inside PRoot. No broad storage permission is needed (MVP scope).
 */
class ProjectRepository(
    private val paths: HarnessPaths,
    private val proot: ProotCommand,
) {
    fun list(): List<Project> {
        paths.projects.mkdirs()
        return paths.projects.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { dir ->
                Project(
                    name = dir.name,
                    guestPath = "/root/projects/${dir.name}",
                    hostPath = dir.absolutePath,
                    isGitRepo = File(dir, ".git").exists(),
                    lastModified = dir.lastModified(),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun create(name: String): Project {
        val safe = sanitize(name)
        val dir = File(paths.projects, safe)
        dir.mkdirs()
        return Project(safe, "/root/projects/$safe", dir.absolutePath, false, dir.lastModified())
    }

    suspend fun clone(url: String, name: String? = null): Result<Project> = withContext(Dispatchers.IO) {
        runCatching {
            val derived = name?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').removeSuffix(".git")
            val safe = sanitize(derived)
            val dir = File(paths.projects, safe)
            if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
                error("Já existe um projeto chamado \"$safe\".")
            }
            val result = ProcessRunner.runCombined(
                proot.argv(listOf(
                    "/usr/bin/git", "clone", "--depth", "1", url, "/root/projects/$safe",
                )),
                proot.hostEnv(emptyMap()),
            )
            if (!result.ok) error("git clone falhou: ${result.tail(12)}")
            Project(safe, "/root/projects/$safe", dir.absolutePath, true, dir.lastModified())
        }
    }

    suspend fun delete(project: Project): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { File(project.hostPath).deleteRecursively(); Unit }
    }

    fun projectFor(path: String): Project? = list().firstOrNull { it.guestPath == path }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "-").ifBlank { "projeto" }
}
