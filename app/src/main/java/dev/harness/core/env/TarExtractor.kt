package dev.harness.core.env

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * Minimal tar extractor for the Alpine minirootfs.
 *
 * The Android runtime has no `tar`, and shelling out to BusyBox is not an
 * option before the rootfs exists — so we implement the subset of the tar
 * format the Alpine tarball uses: regular files, directories, symlinks,
 * hardlinks and PAX/GNU long-name entries. It runs inside PRoot.
 */
object TarExtractor {

    fun extractTarGz(archive: File, destination: File) {
        destination.mkdirs()
        GZIPInputStream(archive.inputStream().buffered(1 shl 16)).use { gz ->
            extractTar(gz, destination)
        }
    }

    private fun extractTar(input: InputStream, destination: File) {
        val header = ByteArray(512)
        var pendingLongName: String? = null
        var pendingLongLink: String? = null
        while (true) {
            if (!readFully(input, header, 512)) break
            if (header.all { it == 0.toByte() }) break // end-of-archive block

            val name = (pendingLongName ?: readString(header, 0, 100)).also { pendingLongName = null }
            val size = readOctal(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val linkName = (pendingLongLink ?: readString(header, 157, 100)).also { pendingLongLink = null }
            val prefix = readString(header, 345, 155)
            val mode = readOctal(header, 100, 8)

            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val target = safeResolve(destination, fullName)

            when (typeFlag) {
                'L' -> { // GNU long name
                    pendingLongName = readStringBlock(input, size)
                    continue
                }
                'K' -> { // GNU long link target
                    pendingLongLink = readStringBlock(input, size)
                    continue
                }
                'x', 'X' -> { // PAX extended header
                    val pax = readStringBlock(input, size)
                    pendingLongName = parsePaxPath(pax) ?: pendingLongName
                    continue
                }
                '5' -> { // directory
                    target.mkdirs()
                    applyMode(target, mode)
                }
                '2' -> { // symlink
                    target.parentFile?.mkdirs()
                    createSymlink(target, linkName)
                }
                '1' -> { // hardlink
                    target.parentFile?.mkdirs()
                    val linkTarget = safeResolve(destination, linkName)
                    createHardlinkOrCopy(linkTarget, target)
                }
                '0', 0.toChar(), '\u0000' -> { // regular file
                    target.parentFile?.mkdirs()
                    writeFile(input, size, target)
                    applyMode(target, mode)
                }
                else -> {
                    // Skip unknown entries (device nodes, fifos) but consume its bytes.
                    skipFully(input, paddedSize(size))
                }
            }
            // Each entry is padded to a 512-byte boundary.
            if (typeFlag in charArrayOf('0', '5', '1', '2', 0.toChar(), '\u0000')) {
                skipFully(input, padding(size))
            }
        }
    }

    private fun writeFile(input: InputStream, size: Long, target: File) {
        target.outputStream().use { out ->
            var remaining = size
            val buf = ByteArray(1 shl 16)
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) throw IOException("Unexpected EOF while extracting ${target.name}")
                out.write(buf, 0, n)
                remaining -= n
            }
        }
    }

    private fun createSymlink(target: File, linkName: String) {
        val linkPath: Path = target.toPath()
        Files.deleteIfExists(linkPath)
        try {
            Files.createSymbolicLink(linkPath, java.nio.file.Paths.get(linkName))
        } catch (_: Throwable) {
            // Fallback: copy the referenced file when symlinks are unavailable.
            val src = File(target.parentFile, linkName)
            if (src.exists() && src.isFile) src.copyTo(target, overwrite = true)
            else target.createNewFile()
        }
    }

    private fun createHardlinkOrCopy(source: File, target: File) {
        try {
            if (target.exists()) target.delete()
            Files.createLink(target.toPath(), source.toPath())
        } catch (_: Throwable) {
            if (source.exists()) source.copyTo(target, overwrite = true) else target.createNewFile()
        }
    }

    private fun applyMode(file: File, mode: Int) {
        val perms = HashSet<PosixFilePermission>()
        if (mode and 0x100 != 0) perms += PosixFilePermission.OWNER_READ
        if (mode and 0x080 != 0) perms += PosixFilePermission.OWNER_WRITE
        if (mode and 0x040 != 0) perms += PosixFilePermission.OWNER_EXECUTE
        if (mode and 0x020 != 0) perms += PosixFilePermission.GROUP_READ
        if (mode and 0x010 != 0) perms += PosixFilePermission.GROUP_WRITE
        if (mode and 0x008 != 0) perms += PosixFilePermission.GROUP_EXECUTE
        if (mode and 0x004 != 0) perms += PosixFilePermission.OTHERS_READ
        if (mode and 0x002 != 0) perms += PosixFilePermission.OTHERS_WRITE
        if (mode and 0x001 != 0) perms += PosixFilePermission.OTHERS_EXECUTE
        runCatching { Files.setPosixFilePermissions(file.toPath(), perms) }
    }

    private fun safeResolve(root: File, name: String): File {
        val cleaned = name.removePrefix("./")
        return File(root, cleaned)
    }

    private fun readString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun readOctal(header: ByteArray, offset: Int, length: Int): Int {
        val raw = String(header, offset, length, Charsets.US_ASCII).trim().trimEnd('\u0000')
        if (raw.isEmpty()) return 0
        return raw.toIntOrNull(8) ?: 0
    }

    private fun readStringBlock(input: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        if (!readFully(input, bytes, size.toInt())) throw IOException("Truncated tar block")
        skipFully(input, padding(size))
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun parsePaxPath(pax: String): String? {
        // Records look like: "<len> path=<value>\n"
        for (record in pax.split('\n')) {
            val eq = record.indexOf('=')
            if (eq > 0 && record.substring(0, eq).trim().endsWith("path")) {
                return record.substring(eq + 1)
            }
        }
        return null
    }

    private fun padding(size: Long): Long = (512 - (size % 512)) % 512

    private fun paddedSize(size: Long): Long = size + padding(size)

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) return read > 0
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(512)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) return
            remaining -= n
        }
    }
}
