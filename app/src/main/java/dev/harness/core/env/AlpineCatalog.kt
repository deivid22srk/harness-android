package dev.harness.core.env

/**
 * Pinned Alpine minirootfs release. The SHA-256 is verified before extraction.
 * Bump these two values together; the provisioner refuses mismatches.
 *
 * Source: https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/latest-releases.yaml
 */
object AlpineCatalog {
    const val BRANCH = "v3.24"
    const val VERSION = "3.24.4"
    const val FILE_NAME = "alpine-minirootfs-3.24.4-aarch64.tar.gz"
    const val SHA256 = "4b14c18f7e90a063b61462ea1462a5732c154bf6adad594974fe6487cd98a8de"
    const val DOWNLOAD_URL =
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/$FILE_NAME"

    /** Packages installed during provisioning. Keep this list minimal. */
    val PACKAGES = listOf(
        "nodejs", "npm", "git", "ripgrep", "bash", "curl", "openssh-client", "ca-certificates",
    )

    /** Node.js requirement of the Pi version pinned here (>= 22.19.0). */
    const val PI_PACKAGE = "@earendil-works/pi-coding-agent"
    const val PI_VERSION = "1.6.0"
}
