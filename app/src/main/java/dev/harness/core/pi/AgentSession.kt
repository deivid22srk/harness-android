package dev.harness.core.pi

import dev.harness.core.env.HarnessPaths
import dev.harness.core.env.ProotCommand
import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.ExtensionUiRequest
import dev.harness.core.rpc.PiEvent
import dev.harness.core.rpc.PiEventRecord
import dev.harness.core.rpc.PiExtensionUiRecord
import dev.harness.core.rpc.PiMalformedRecord
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.PiRecord
import dev.harness.core.rpc.PiResponseRecord
import dev.harness.core.rpc.RpcClient
import dev.harness.core.state.AgentStatus
import dev.harness.core.state.ChatItem
import dev.harness.core.state.ChatReducer
import dev.harness.core.state.ChatState
import dev.harness.core.state.ModelInfo
import dev.harness.core.state.NoticeLevel
import dev.harness.core.state.toAssistantItem
import dev.harness.core.state.toUserItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class ConnectionState { Stopped, Starting, Connected, Restarting, Failed }

/** One-shot user-facing notifications originating from the session. */
data class SessionNotice(val text: String, val level: NoticeLevel)

/**
 * Orchestrates the whole agent: starts the Pi process inside PRoot, speaks RPC,
 * reduces events into UI state, and answers extension-UI requests.
 *
 * Rendering is throttled to ~30 fps to save CPU/battery during streaming
 * (PROMPT §6). The reducer stays pure; throttling only coalesces publications.
 */
class AgentSession(
    private val paths: HarnessPaths,
    private val proot: ProotCommand,
    private val scope: CoroutineScope,
) {
    private val reducer = ChatReducer()
    private val stateFlow = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = stateFlow.asStateFlow()

    private val connectionFlow = MutableStateFlow(ConnectionState.Stopped)
    val connection: StateFlow<ConnectionState> = connectionFlow.asStateFlow()

    private val uiRequestQueue = Channel<ExtensionUiRequest>(Channel.UNLIMITED)
    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 32)
    val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    private val _pendingUi = MutableStateFlow<List<ExtensionUiRequest>>(emptyList())
    val pendingUi: StateFlow<List<ExtensionUiRequest>> = _pendingUi.asStateFlow()

    private var pi: PiProcess? = null
    private var rpc: RpcClient? = null
    private var internal = ChatState()
    private val dirty = AtomicBoolean(false)
    private var pumpJob: Job? = null
    private var hudJob: Job? = null
    private var stderrJob: Job? = null

    /** Bounded stderr tail, safe to read from any thread ([stderrLog]). */
    private val stderrTail = ConcurrentLinkedDeque<String>()

    /** Consecutive crash-restart attempts (reset on a successful handshake). */
    private val restartAttempts = AtomicInteger(0)

    private var config: PiLaunchConfig = PiLaunchConfig()

    suspend fun start(launch: PiLaunchConfig): Boolean {
        config = launch
        connectionFlow.value = ConnectionState.Starting
        stopInternal()
        if (!paths.hasProotBinaries) {
            connectionFlow.value = ConnectionState.Failed
            publish(ChatState(error = "Binários do ambiente ausentes no APK."))
            return false
        }
        val process = try {
            PiProcess(paths, proot).also { it.start(launch) }
        } catch (t: Throwable) {
            connectionFlow.value = ConnectionState.Failed
            publish(ChatState(error = "Não foi possível iniciar o agente: ${t.message}"))
            return false
        }
        pi = process
        drainStderr(process)

        val osProcess = process.process ?: run {
            connectionFlow.value = ConnectionState.Failed
            publish(ChatState(error = "O processo do agente não iniciou."))
            return false
        }
        val client = RpcClient(
            stdin = osProcess.outputStream,
            stdout = osProcess.inputStream,
            scope = scope,
        )
        rpc = client
        // Subscribe to the record stream before the reader starts so no event
        // emitted during startup/handshake is lost. The client replays a small
        // buffer to cover the subscription race.
        startPump(client)
        client.start()
        startPublisher()

        // Handshake: state, models, and existing messages. All four commands
        // must succeed before we report Connected — otherwise the UI would
        // show a healthy session with empty state.
        val stateResp = client.call(cmd("get_state"), timeoutMs = 15_000)
            ?: run {
                connectionFlow.value = ConnectionState.Failed
                publish(ChatState(error = "O agente não respondeu ao handshake (get_state)."))
                stopInternal()
                return false
            }
        hydrateState(stateResp)
        client.call(cmd("get_available_models"))
        client.call(cmd("get_messages"))
        client.call(cmd("get_available_thinking_levels"))

        watchProcess(process)
        restartAttempts.set(0)
        connectionFlow.value = ConnectionState.Connected
        return true
    }

    fun stop() {
        stopInternal()
        connectionFlow.value = ConnectionState.Stopped
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    suspend fun sendPrompt(text: String, images: List<ImageAttachment> = emptyList()) {
        if (text.isBlank() && images.isEmpty()) return
        val busy = internal.isBusy
        // Optimistically append the user's message (Pi echoes it back via events,
        // but showing it immediately keeps the composer responsive).
        appendUserLocally(text, images.size)
        val imageJson = if (images.isEmpty()) null else JsonArray(
            images.map { img ->
                buildJsonObject {
                    put("type", "image")
                    put("data", img.base64Data)
                    put("mimeType", img.mimeType)
                }
            },
        )
        val command = buildJsonObject {
            put("type", "prompt")
            put("message", text)
            imageJson?.let { put("images", it) }
            if (busy) put("streamingBehavior", "followUp")
        }
        rpc?.call(command, timeoutMs = 20_000)
    }

    suspend fun steer(text: String) {
        if (text.isBlank()) return
        appendUserLocally(text, 0)
        rpc?.call(buildJsonObject { put("type", "steer"); put("message", text) })
    }

    suspend fun followUp(text: String) {
        if (text.isBlank()) return
        appendUserLocally(text, 0)
        rpc?.call(buildJsonObject { put("type", "follow_up"); put("message", text) })
    }

    suspend fun abort() {
        rpc?.call(buildJsonObject { put("type", "abort") }, timeoutMs = 60_000)
    }

    suspend fun newSession() {
        rpc?.call(buildJsonObject { put("type", "new_session") })
        publish(ChatState(sessionName = internal.sessionName))
        rpc?.call(cmd("get_messages"))
    }

    suspend fun setModel(provider: String, modelId: String) {
        rpc?.call(buildJsonObject {
            put("type", "set_model")
            put("provider", provider)
            put("modelId", modelId)
        })
    }

    suspend fun setThinkingLevel(level: String) {
        rpc?.call(buildJsonObject { put("type", "set_thinking_level"); put("level", level) })
    }

    suspend fun setSessionName(name: String) {
        rpc?.call(buildJsonObject { put("type", "set_session_name"); put("name", name) })
    }

    /**
     * Asks the running Pi for its configured models (built-in catalog plus
     * custom entries from models.json). Returns qualified `provider/id` pairs.
     */
    suspend fun listAvailableModels(): List<Pair<String, String>> {
        val client = rpc ?: return emptyList()
        val response = client.call(cmd("get_available_models"), timeoutMs = 20_000) ?: return emptyList()
        val models = response.data?.get("models") as? JsonArray ?: return emptyList()
        return models.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val provider = (obj["provider"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            provider to id
        }
    }

    suspend fun refreshStats() {
        val resp = rpc?.call(cmd("get_session_stats"))
        val percent = resp?.data?.get("contextUsage")?.let { usage ->
            val obj = usage as? JsonObject ?: return@let null
            (obj["percent"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
        }
        if (percent != null) publish(internal.copy(contextPercent = percent))
    }

    suspend fun respondExtensionUi(id: String, value: String? = null, confirmed: Boolean? = null, cancelled: Boolean = false) {
        val payload = buildJsonObject {
            put("type", "extension_ui_response")
            put("id", id)
            when {
                cancelled -> put("cancelled", true)
                confirmed != null -> put("confirmed", confirmed)
                value != null -> put("value", value)
                else -> put("cancelled", true)
            }
        }
        rpc?.call(payload, timeoutMs = 5_000)
        _pendingUi.value = _pendingUi.value.filterNot { it.id == id }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun startPump(client: RpcClient) {
        pumpJob?.cancel()
        pumpJob = scope.launch(Dispatchers.Default) {
            client.records.collect { record -> handleRecord(record) }
        }
    }

    private fun startPublisher() {
        if (hudJob?.isActive == true) return
        hudJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(FRAME_MS)
                if (dirty.getAndSet(false)) stateFlow.value = internal
            }
        }
    }

    private fun handleRecord(record: PiRecord) {
        when (record) {
            is PiEventRecord -> reduce(record.event)
            is PiResponseRecord -> handleResponse(record)
            is PiExtensionUiRecord -> handleExtensionUi(record.request)
            is PiMalformedRecord -> { /* Ignored: Pi also emits non-protocol noise on stdout rarely. */ }
        }
    }

    private fun reduce(event: PiEvent) {
        internal = reducer.reduce(internal, event)
        dirty.set(true)
    }

    private fun handleResponse(response: PiResponseRecord) {
        when (response.command) {
            "get_state" -> hydrateState(response)
            "get_messages" -> hydrateMessages(response)
            "get_available_models" -> hydrateModels(response)
            "get_available_thinking_levels" -> hydrateThinkingLevels(response)
            else -> Unit
        }
        if (!response.success && response.error != null && response.command == "prompt") {
            _notices.tryEmit(SessionNotice(response.error, NoticeLevel.Error))
        }
    }

    private fun hydrateState(response: PiResponseRecord?) {
        val data = response?.data ?: return
        internal = internal.copy(
            model = parseModel(data["model"] as? JsonObject),
            thinkingLevel = data.stringOrNull("thinkingLevel") ?: internal.thinkingLevel,
            sessionName = data.stringOrNull("sessionName") ?: internal.sessionName,
        )
        dirty.set(true)
    }

    private fun hydrateThinkingLevels(response: PiResponseRecord?) {
        val levels = (response?.data?.get("levels") as? JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: return
        internal = internal.copy(availableThinkingLevels = levels)
        dirty.set(true)
    }

    private fun hydrateModels(response: PiResponseRecord?) {
        val models = response?.data?.get("models") as? JsonArray ?: return
        val infos = models.mapNotNull { (it as? JsonObject)?.let(::parseModel) }
        internal = internal.copy(
            model = internal.model ?: infos.firstOrNull(),
        )
        dirty.set(true)
    }

    private fun hydrateMessages(response: PiResponseRecord?) {
        val messages = response?.data?.get("messages") as? JsonArray ?: return
        val items = ArrayList<ChatItem>()
        var toolCounter = 0
        messages.forEachIndexed { index, el ->
            val msg = PiMessage.from(el) ?: return@forEachIndexed
            when (msg.role) {
                "user" -> {
                    val item = msg.toUserItem("h-user-$index")
                    if (item.text.isNotBlank()) items += item
                }
                "assistant" -> {
                    val hasVisible = msg.blocks.any {
                        (it is ContentBlock.Text && it.text.isNotBlank()) ||
                            (it is ContentBlock.Thinking && it.thinking.isNotBlank())
                    }
                    if (hasVisible) items += msg.toAssistantItem("h-asst-$index")
                }
                "toolResult" -> Unit // grouped by tool_execution events; skip on hydrate
                else -> Unit
            }
        }
        internal = internal.copy(items = items)
        dirty.set(true)
    }

    private fun handleExtensionUi(request: ExtensionUiRequest) {
        if (request.isDialog) {
            _pendingUi.value = _pendingUi.value + request
        } else {
            when (request.method) {
                "notify" -> {
                    val level = when (request.notifyType) {
                        "error" -> NoticeLevel.Error
                        "warning" -> NoticeLevel.Warning
                        else -> NoticeLevel.Info
                    }
                    request.message?.let { _notices.tryEmit(SessionNotice(it, level)) }
                }
                else -> Unit // setStatus/setWidget/setTitle are TUI-only concerns.
            }
        }
    }

    private fun appendUserLocally(text: String, imageCount: Int) {
        internal = internal.copy(
            items = internal.items + ChatItem.UserMessage(
                id = "u-${internal.items.size}-${System.nanoTime() % 100000}",
                text = text,
                imageCount = imageCount,
            ),
            error = null,
        )
        dirty.set(true)
    }

    private fun publish(newState: ChatState) {
        internal = newState
        stateFlow.value = newState
    }

    private fun drainStderr(process: PiProcess) {
        val osProcess = process.process ?: return
        stderrJob?.cancel()
        stderrJob = scope.launch(Dispatchers.IO) {
            runCatching {
                osProcess.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        stderrTail.addLast(line)
                        while (stderrTail.size > STDERR_TAIL_LINES) stderrTail.pollFirst()
                    }
                }
            }
        }
    }

    private fun watchProcess(process: PiProcess) {
        val osProcess = process.process ?: return
        scope.launch(Dispatchers.IO) {
            val exit = runCatching { osProcess.waitFor() }.getOrDefault(-1)
            if (connectionFlow.value == ConnectionState.Stopped) return@launch

            // Bounded restart with exponential backoff: 2s, 4s, 8s. After
            // MAX_RESTARTS consecutive crashes we stop and surface the failure
            // instead of looping forever (a bad config would never recover).
            val attempt = restartAttempts.incrementAndGet()
            if (attempt > MAX_RESTARTS) {
                connectionFlow.value = ConnectionState.Failed
                _notices.tryEmit(
                    SessionNotice(
                        "O agente falhou $attempt vezes seguidas (último código $exit). " +
                            "Verifique a configuração em Configurações → Diagnóstico.",
                        NoticeLevel.Error,
                    ),
                )
                publish(ChatState(error = "O agente não pôde ser mantido em execução."))
                return@launch
            }
            val backoff = RESTART_BASE_MS shl (attempt - 1)
            connectionFlow.value = ConnectionState.Restarting
            _notices.tryEmit(
                SessionNotice(
                    "O agente encerrou inesperadamente (código $exit). Tentando novamente…",
                    NoticeLevel.Warning,
                ),
            )
            delay(backoff)
            start(config)
        }
    }

    private fun stopInternal() {
        stderrJob?.cancel()
        pumpJob?.cancel()
        hudJob?.cancel()
        rpc?.close()
        pi?.stop()
        rpc = null
        pi = null
    }

    fun stderrLog(): String = stderrTail.joinToString("\n")

    private companion object {
        const val FRAME_MS = 33L
        const val STDERR_TAIL_LINES = 200
        const val MAX_RESTARTS = 3
        const val RESTART_BASE_MS = 2_000L
    }
}

data class ImageAttachment(val base64Data: String, val mimeType: String)

// ---------------------------------------------------------------------------
// Small JSON helpers
// ---------------------------------------------------------------------------

private fun cmd(type: String): JsonObject = buildJsonObject { put("type", type) }

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

private fun parseModel(obj: JsonObject?): ModelInfo? {
    if (obj == null) return null
    val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
    return ModelInfo(
        provider = (obj["provider"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(),
        id = id,
        name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: id,
        reasoning = (obj["reasoning"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() ?: false,
    )
}
