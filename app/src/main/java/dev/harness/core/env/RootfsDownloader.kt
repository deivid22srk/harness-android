package dev.harness.core.env

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the Alpine minirootfs with a lightweight [HttpURLConnection]
 * (no Retrofit/OkHttp, per the "keep the APK small" principle).
 *
 * Follows redirects, reports progress, resumes nothing (the tarball is small,
 * ~4 MB, and a partial file is discarded on failure) and verifies SHA-256.
 */
class RootfsDownloader {

    class Progress(val downloaded: Long, val total: Long) {
        val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total) else 0f
    }

    @Throws(IOException::class)
    fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        onProgress: (Progress) -> Unit,
    ): File {
        destination.parentFile?.mkdirs()
        val tmp = File(destination.parentFile, destination.name + ".part")
        if (tmp.exists()) tmp.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(url)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var read = 0L
                    var lastReported = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (read - lastReported >= 256 * 1024) {
                            lastReported = read
                            onProgress(Progress(read, total))
                        }
                    }
                    output.flush()
                    onProgress(Progress(read, total))
                }
            }
        } finally {
            connection?.disconnect()
        }

        val actual = sha256(tmp)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            tmp.delete()
            throw IOException("Checksum mismatch: expected $expectedSha256, got $actual")
        }
        if (destination.exists()) destination.delete()
        if (!tmp.renameTo(destination)) {
            tmp.copyTo(destination, overwrite = true)
            tmp.delete()
        }
        return destination
    }

    private fun openFollowingRedirects(url: String, maxHops: Int = 5): HttpURLConnection {
        var current = url
        repeat(maxHops) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Harness-Android")
            }
            when (conn.responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) throw IOException("Redirect without Location")
                    current = if (location.startsWith("http")) location
                    else URL(URL(current), location).toString()
                }
                200 -> return conn
                else -> {
                    val code = conn.responseCode
                    conn.disconnect()
                    throw IOException("HTTP $code for $current")
                }
            }
        }
        throw IOException("Too many redirects for $url")
    }

    private fun sha256(file: File): String {
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
