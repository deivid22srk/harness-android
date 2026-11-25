package dev.harness.core.env

import java.io.File
import java.security.MessageDigest

/**
 * Builds the PRoot argv used to enter the Alpine environment.
 *
 * The PRoot binary is packaged as `libproot.so` and executed from
 * `applicationInfo.nativeLibraryDir`, the only place where the app may `exec`
 * since Android 10 (W^X). `libproot-loader.so` is passed via `PROOT_LOADER`
 * for the same reason: the loader can never be extracted into filesDir and
 * executed there.
 *
 * See docs/ARCHITECTURE.md § Linux layer for the rationale of every flag.
 */
class ProotCommand(private val paths: HarnessPaths) {

    /** Environment variables needed by PRoot itself (not by the guest). */
    fun hostEnv(apiKeys: Map<String, String>): Map<String, String> {
        val env = HashMap<String, String>()
        env["PROOT_TMP_DIR"] = paths.tmp.absolutePath
        env["PROOT_LOADER"] = paths.prootLoader.absolutePath
        // libproot.so is self-contained (talloc + ashmem shim are static) and
        // only needs Bionic system libraries, but pointing the loader at
        // nativeLibraryDir is harmless and helps if that ever changes.
        env["LD_LIBRARY_PATH"] = paths.nativeLibDir.absolutePath
        env.putAll(apiKeys)
        return env
    }

    /**
     * argv that runs [guestCmd] inside the rootfs.
     * `-0` fakes uid 0, `--link2symlink` emulates hardlinks (unsupported on /data),
     * `--kill-on-exit` avoids orphaned tracees.
     */
    fun argv(
        guestCmd: List<String>,
        guestEnv: Map<String, String> = emptyMap(),
        workDir: String = "/root",
    ): List<String> {
        val argv = mutableListOf(
            paths.prootBinary.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r", paths.rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${paths.home.absolutePath}:/root",
            "-w", workDir,
        )
        // Fake files for Android's restricted /proc so Node's os.cpus() and
        // friends do not crash. Only added when present in the rootfs.
        for (fake in FAKE_PROC_FILES) {
            val f = File(paths.rootfs, fake)
            if (f.exists()) argv += listOf("-b", f.absolutePath)
        }
        val baseEnv = mapOf(
            "HOME" to "/root",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "TERM" to "dumb",
            "TMPDIR" to "/tmp",
        )
        argv += "/usr/bin/env"
        argv += "-i"
        (baseEnv + guestEnv).forEach { (k, v) -> argv += "$k=$v" }
        argv += guestCmd
        return argv
    }

    companion object {
        private val FAKE_PROC_FILES = listOf(
            "proc/stat",
            "proc/version",
            "proc/loadavg",
            "proc/uptime",
            "proc/cpuinfo",
        )

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
