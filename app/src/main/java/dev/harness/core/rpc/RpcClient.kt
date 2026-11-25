package dev.harness.core.rpc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Speaks the Pi RPC protocol (JSONL over stdin/stdout).
 *
 * - Reads stdout continuously and splits records with [JsonlFramer].
 * - Writes commands serialized through a channel so stdin backpressure is honored.
 * - Correlates `response` records to `call()` suspension points by `id`.
 *
 * The client is transport-agnostic: it only needs the child process's streams so
 * it can be exercised in unit tests with in-memory pipes.
 */
class RpcClient(
    private val stdin: OutputStream,
    private val stdout: InputStream,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val ids = AtomicLong(0)
    private val outbound = Channel<String>(capacity = Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<String, Channel<PiResponseRecord>>()
    private val writeLock = Mutex()

    private val _records = MutableSharedFlow<PiRecord>(replay = 128, extraBufferCapacity = 512)
    /** Every inbound record (responses, events, extension UI, malformed). */
    val records: SharedFlow<PiRecord> = _records.asSharedFlow()

    private var readerJob: Job? = null
    private var writerJob: Job? = null
    @Volatile private var closed = false

    fun start() {
        readerJob = scope.launch(ioDispatcher) { readLoop() }
        writerJob = scope.launch(ioDispatcher) { writeLoop() }
    }

    private suspend fun readLoop() {
        val framer = JsonlFramer()
        val buf = ByteArray(16 * 1024)
        try {
            while (!closed) {
                val n = stdout.read(buf)
                if (n < 0) break
                if (n == 0) continue
                for (line in framer.feed(buf, n)) {
                    if (line.isBlank()) continue
                    val record = PiJson.parse(line) ?: continue
                    dispatch(record)
                }
            }
        } catch (_: Throwable) {
            // Stream closed / process died; the process supervisor handles restart.
        } finally {
            // Unblock any waiters so callers don't hang forever.
            pending.values.forEach { it.trySend(PiResponseRecord(null, "", false, null, "stream closed")) }
            pending.clear()
        }
    }

    private suspend fun dispatch(record: PiRecord) {
        if (record is PiResponseRecord && record.id != null) {
            pending.remove(record.id)?.trySend(record)
        }
        _records.emit(record)
    }

    private suspend fun writeLoop() {
        for (line in outbound) {
            try {
                writeLock.withLock {
                    stdin.write(line.toByteArray(Charsets.UTF_8))
                    stdin.write('\n'.code)
                    stdin.flush()
                }
            } catch (_: Throwable) {
                return
            }
        }
    }

    /** Enqueue a raw command object. Returns the generated id. */
    fun sendRaw(command: JsonObject): String {
        val id = command["id"]?.let { (it as? JsonPrimitive)?.content } ?: nextId()
        val withId = if (command.containsKey("id")) command else buildJsonObject {
            put("id", id)
            command.forEach { (k, v) -> put(k, v) }
        }
        outbound.trySend(PiJson.json.encodeToString(JsonObject.serializer(), withId))
        return id
    }

    /** Send a command and wait for its correlated response. */
    suspend fun call(command: JsonObject, timeoutMs: Long = 30_000): PiResponseRecord? {
        val id = nextId()
        val withId = buildJsonObject {
            put("id", id)
            command.forEach { (k, v) -> put(k, v) }
        }
        val waiter = Channel<PiResponseRecord>(capacity = 1)
        pending[id] = waiter
        outbound.trySend(PiJson.json.encodeToString(JsonObject.serializer(), withId))
        return withTimeoutOrNull(timeoutMs) { waiter.receive() }.also { pending.remove(id) }
    }

    fun close() {
        closed = true
        outbound.close()
        readerJob?.cancel()
        writerJob?.cancel()
    }

    private fun nextId(): String = "h-${ids.incrementAndGet()}"
}
