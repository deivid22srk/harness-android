package dev.harness.core.env

import android.content.Context
import java.io.File

/**
 * Canonical filesystem layout inside the app sandbox.
 *
 *   files/
 *     env/rootfs/          extracted Alpine minirootfs
 *     env/version          marker: provisioned rootfs version
 *     home/                bind-mounted to /root inside PRoot
 *     home/projects/       user projects
 *     home/.pi/            Pi agent config, sessions
 *     tmp/                 PROOT_TMP_DIR and scratch space
 *     exports/             exported session HTML
 *     logs/                diagnostics
 */
class HarnessPaths(context: Context) {
    val filesDir: File = context.filesDir
    val envDir = File(filesDir, "env")
    val rootfs = File(envDir, "rootfs")
    val versionMarker = File(envDir, "version")
    val home = File(filesDir, "home")
    val projects = File(home, "projects")
    val piDir = File(home, ".pi")
    val tmp = File(filesDir, "tmp")
    val exports = File(filesDir, "exports")
    val logs = File(filesDir, "logs")

    /** Directory where Android extracts the packaged .so files; they are executable. */
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir)
    val prootBinary = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    /** True when the CI build placed the PRoot binaries into the APK. */
    val hasProotBinaries: Boolean
        get() = prootBinary.exists() && prootBinary.length() > 0 &&
            prootLoader.exists() && prootLoader.length() > 0

    fun ensureDirs() {
        listOf(envDir, home, projects, piDir, tmp, exports, logs).forEach { it.mkdirs() }
    }

    /** Absolute path as seen by the guest (PRoot makes real paths start with the rootfs). */
    fun guestHome(): String = "/root"
}
