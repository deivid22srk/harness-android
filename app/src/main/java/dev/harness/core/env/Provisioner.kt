package dev.harness.core.env

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Brings up the Alpine environment: downloads and verifies the minirootfs,
 * extracts it, configures DNS/APK and installs Node + the Pi coding agent.
 *
 * Every step is idempotent and reported through [state] so the onboarding UI
 * can show calm, non-technical progress.
 */
class Provisioner(
    private val paths: HarnessPaths,
    private val proot: ProotCommand,
    private val downloader: RootfsDownloader = RootfsDownloader(),
) {
    private val _state = MutableStateFlow<ProvisionState>(ProvisionState.Idle)
    val state: StateFlow<ProvisionState> = _state.asStateFlow()

    private val versionFile = paths.versionMarker

    /** True when a previous provisioning succeeded for the pinned version. */
    fun isProvisioned(): Boolean =
        paths.hasProotBinaries &&
            File(paths.rootfs, "etc/alpine-release").exists() &&
            versionFile.readTextOrNull()?.trim() == marker()

    private fun marker() = "alpine=${AlpineCatalog.VERSION};pi=${AlpineCatalog.PI_VERSION}"

    suspend fun provision(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!paths.hasProotBinaries) {
            _state.value = ProvisionState.Failed(
                message = "Binários do ambiente ausentes no APK.",
                diagnostics = "libproot.so / libproot-loader.so não encontrados em ${paths.nativeLibDir}",
            )
            return@withContext false
        }
        if (!force && isProvisioned()) {
            val nv = guestVersion("node")
            val pv = guestVersion("pi")
            _state.value = ProvisionState.Ready(nv, pv)
            return@withContext true
        }

        paths.ensureDirs()
        try {
            _state.value = ProvisionState.Preparing("Verificando ambiente")
            if (force && paths.rootfs.exists()) paths.rootfs.deleteRecursively()
            paths.rootfs.mkdirs()

            val tarball = File(paths.envDir, AlpineCatalog.FILE_NAME)
            if (!tarball.exists()) {
                _state.value = ProvisionState.Downloading(0f, "Baixando ambiente")
                downloader.download(AlpineCatalog.DOWNLOAD_URL, tarball, AlpineCatalog.SHA256) { p ->
                    _state.value = ProvisionState.Downloading(p.fraction, "Baixando ambiente")
                }
            }

            _state.value = ProvisionState.Extracting("Extraindo ambiente")
            if (!File(paths.rootfs, "etc/alpine-release").exists()) {
                TarExtractor.extractTarGz(tarball, paths.rootfs)
            }
            configureRootfs()

            installPackages()

            _state.value = ProvisionState.InstallingAgent("Preparando o Pi")
            installAgent()

            val nodeVersion = guestVersion("node")
            val piVersion = guestVersion("pi")
            if (piVersion.isBlank()) {
                throw IllegalStateException("pi não respondeu após a instalação")
            }
            versionFile.writeText(marker())
            _state.value = ProvisionState.Ready(nodeVersion, piVersion)
            true
        } catch (t: Throwable) {
            _state.value = ProvisionState.Failed(
                message = t.message ?: "Erro desconhecido na preparação",
                diagnostics = t.stackTraceToString(),
            )
            false
        }
    }

    private fun configureRootfs() {
        // DNS + hosts so apk/npm can reach the network.
        File(paths.rootfs, "etc/resolv.conf").writeText(
            "nameserver 1.1.1.1\nnameserver 8.8.8.8\n",
        )
        File(paths.rootfs, "etc/hosts").writeText(
            "127.0.0.1\tlocalhost\n::1\t\tlocalhost\n",
        )
        // Fake /proc files: Android's /proc rejects reads the Node runtime makes.
        val procDir = File(paths.rootfs, "proc")
        procDir.mkdirs()
        val ncpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuinfo = buildString {
            repeat(ncpu) { i ->
                appendLine("processor\t: $i")
                appendLine("BogoMIPS\t: 38.40")
                appendLine("model name\t: arm64")
                appendLine()
            }
        }
        File(procDir, "cpuinfo").writeText(cpuinfo)
        File(procDir, "version").writeText("Linux version 5.10.0 (harness) \n")
        File(procDir, "loadavg").writeText("0.00 0.00 0.00 1/$ncpu 1\n")
        File(procDir, "uptime").writeText("100.00 100.00\n")
        val perCpu = buildString {
            repeat(ncpu) { i -> appendLine("cpu$i 0 0 0 0 0 0 0 0 0 0") }
            appendLine("intr 0")
            appendLine("ctxt 0")
            appendLine("btime 0")
            appendLine("processes 0")
            appendLine("procs_running 1")
            appendLine("procs_blocked 0")
        }
        File(procDir, "stat").writeText(perCpu)
    }

    private fun installPackages() {
        val pkgCmd = listOf("/sbin/apk", "add", "--no-cache") + AlpineCatalog.PACKAGES
        val steps = 2
        _state.value = ProvisionState.InstallingPackages(1, steps, "Atualizando índices")
        var result = runGuest(listOf("/sbin/apk", "update"))
        if (!result.ok) {
            throw IllegalStateException("apk update falhou: ${result.tail()}")
        }
        _state.value = ProvisionState.InstallingPackages(2, steps, "Instalando Node e ferramentas")
        result = runGuest(pkgCmd)
        if (!result.ok) {
            throw IllegalStateException("apk add falhou: ${result.tail()}")
        }
    }

    private fun installAgent() {
        val npmCmd = listOf(
            "/usr/bin/npm", "install", "-g",
            "${AlpineCatalog.PI_PACKAGE}@${AlpineCatalog.PI_VERSION}",
        )
        val result = runGuest(npmCmd)
        if (!result.ok) {
            throw IllegalStateException("npm install falhou: ${result.tail()}")
        }
    }

    private fun guestVersion(binary: String): String {
        val result = runGuest(listOf(binary, "--version"))
        return if (result.ok) result.output.trim().lineSequence().firstOrNull()?.trim().orEmpty() else ""
    }

    private fun runGuest(cmd: List<String>): ProcessRunner.Result =
        ProcessRunner.runCombined(proot.argv(cmd), proot.hostEnv(emptyMap()))

    private fun File.readTextOrNull(): String? = runCatching { if (exists()) readText() else null }.getOrNull()
}
