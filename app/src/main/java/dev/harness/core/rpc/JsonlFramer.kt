package dev.harness.core.rpc

/**
 * Strict JSONL framing for the Pi RPC protocol.
 *
 * Rules (docs: rpc.md "Framing"):
 *  - Records are separated by a single LF (`\n`, byte 0x0A).
 *  - An optional preceding CR is stripped (CRLF tolerated).
 *  - U+2028 (E2 80 A8) and U+2029 (E2 80 A9) are valid inside JSON strings and
 *    MUST NOT be treated as record boundaries. Because UTF-8 continuation bytes
 *    are always >= 0x80, splitting at the byte level on 0x0A is inherently safe.
 *
 * This class is deliberately free of Android dependencies so it can be unit
 * tested on the JVM.
 */
class JsonlFramer {
    private val buffer = ArrayList<Byte>(256)

    /** Feed arbitrary byte chunks (a chunk may split a UTF-8 sequence). */
    fun feed(chunk: ByteArray, length: Int = chunk.size): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until length) {
            val b = chunk[i]
            if (b == LF) {
                out += decodeLine()
            } else if (b == CR) {
                // Keep it; we strip a single trailing CR when the line is decoded.
                buffer.add(b)
            } else {
                buffer.add(b)
            }
        }
        return out
    }

    private fun decodeLine(): String {
        var end = buffer.size
        if (end > 0 && buffer[end - 1] == CR) end -= 1
        val bytes = ByteArray(end)
        for (i in 0 until end) bytes[i] = buffer[i]
        buffer.clear()
        return String(bytes, Charsets.UTF_8)
    }

    /** Bytes buffered without a terminating LF (used for diagnostics). */
    fun pending(): Int = buffer.size

    /** Reset all buffered state (e.g. after a process restart). */
    fun reset() = buffer.clear()

    private companion object {
        const val LF: Byte = 0x0A
        const val CR: Byte = 0x0D
    }
}
