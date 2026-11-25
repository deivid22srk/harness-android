package dev.harness.core.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Verifies the tar reader against a hand-built archive containing the entry
 * types the Alpine minirootfs actually uses.
 */
class TarExtractorTest {

    @Test
    fun `extracts regular file and directory`() {
        val dir = createTempDir()
        val archive = ByteArrayOutputStream().also { raw ->
            GZIPOutputStream(raw).use { gz ->
                gz.write(dirEntry("./etc/", 0x1ED)) // 0755
                gz.write(fileEntry("./etc/alpine-release", "3.24.4\n", 0x1A4)) // 0644
                gz.write(ByteArray(1024)) // end-of-archive
            }
        }.toByteArray()

        val tarGz = kotlin.io.path.createTempFile("mini", ".tar.gz").toFile().apply { writeBytes(archive) }
        val out = createTempDir()
        TarExtractor.extractTarGz(tarGz, out)

        val release = java.io.File(out, "etc/alpine-release")
        assertTrue(release.exists())
        assertEquals("3.24.4", release.readText().trim())
    }

    @Test
    fun `extracts symlink`() {
        val archive = ByteArrayOutputStream().also { raw ->
            GZIPOutputStream(raw).use { gz ->
                gz.write(fileEntry("./usr/bin/busybox", "binary", 0x1ED))
                gz.write(symlinkEntry("./bin/sh", "../usr/bin/busybox"))
                gz.write(ByteArray(1024))
            }
        }.toByteArray()
        val tarGz = kotlin.io.path.createTempFile("mini", ".tar.gz").toFile().apply { writeBytes(archive) }
        val out = createTempDir()
        TarExtractor.extractTarGz(tarGz, out)

        val link = java.io.File(out, "bin/sh")
        assertTrue("symlink target should exist", link.exists() || java.nio.file.Files.isSymbolicLink(link.toPath()))
    }

    @Test
    fun `handles GNU long names`() {
        val longName = "./" + "a".repeat(150) + "/file.txt"
        val archive = ByteArrayOutputStream().also { raw ->
            GZIPOutputStream(raw).use { gz ->
                gz.write(longNameEntry(longName))
                gz.write(fileEntry("ignored", "hi", 0x1A4))
                gz.write(ByteArray(1024))
            }
        }.toByteArray()
        val tarGz = kotlin.io.path.createTempFile("mini", ".tar.gz").toFile().apply { writeBytes(archive) }
        val out = createTempDir()
        TarExtractor.extractTarGz(tarGz, out)
        assertTrue(java.io.File(out, longName.removePrefix("./")).exists())
    }

    // -- tiny tar builder helpers -------------------------------------------

    private fun header(name: String, size: Long, mode: Int, type: Char, link: String = ""): ByteArray {
        val h = ByteArray(512)
        fun putString(value: String, offset: Int, length: Int) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            System.arraycopy(bytes, 0, h, offset, minOf(bytes.size, length))
        }
        putString(name, 0, 100)
        putString("%07o".format(mode), 100, 8)
        putString("%07o".format(0), 108, 8)
        putString("%07o".format(0), 116, 8)
        putString("%011o".format(size), 124, 12)
        putString("%011o".format(0), 136, 12)
        putString("        ", 148, 8)
        h[156] = type.code.toByte()
        putString(link, 157, 100)
        putString("ustar", 257, 6)
        putString("00", 263, 2)
        // checksum
        var sum = 0
        for (b in h) sum += (b.toInt() and 0xFF)
        putString("%06o\u0000 ".format(sum), 148, 8)
        return h
    }

    private fun fileEntry(name: String, content: String, mode: Int): ByteArray {
        val data = content.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(header(name, data.size.toLong(), mode, '0'))
        out.write(data)
        val pad = (512 - (data.size % 512)) % 512
        out.write(ByteArray(pad))
        return out.toByteArray()
    }

    private fun dirEntry(name: String, mode: Int): ByteArray =
        header(name, 0, mode, '5')

    private fun symlinkEntry(name: String, target: String): ByteArray =
        header(name, 0, 0x1FF, '2', link = target)

    private fun longNameEntry(longName: String): ByteArray {
        val data = longName.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val out = ByteArrayOutputStream()
        out.write(header("././@LongLink", data.size.toLong(), 0x1A4, 'L'))
        out.write(data)
        out.write(ByteArray((512 - (data.size % 512)) % 512))
        return out.toByteArray()
    }

    private fun createTempDir(): java.io.File =
        java.nio.file.Files.createTempDirectory("tar-test").toFile()
}
