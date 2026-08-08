package io.amarwave

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * AmarWave real-time WebSocket client for Android / JVM.
 *
 * ```kotlin
 * val aw = AmarWave(AmarWaveConfig(
 *     appKey    = "YOUR_APP_KEY",
 *     appSecret = "YOUR_APP_SECRET",
 *     cluster   = "default",
 *     forceTLS  = true,
 * ))
 *
 * aw.connection.bind("connected") {
 *     Log.d("AW", "Connected: ${aw.connection.socketId}")
 * }
 *
 * val ch = aw.subscribe("chat-room")
 * ch.bind("subscribed") { ch.publish("hello", mapOf("user" to "Ali")) }
 * ch.bind("hello")      { data -> Log.d("AW", data.toString()) }
 * ```
 */
class AmarWave(private val config: AmarWaveConfig) : EventEmitter() {

    // ── Public ────────────────────────────────────────────────────────────────

    /** Pusher-compatible connection proxy. Bind lifecycle events here. */
    val connection = Connection { socketId }

    /** Current connection state. */
    var state: ConnectionState = ConnectionState.INITIALIZED
        private set

    /** Socket ID assigned by the server. Null when disconnected. */
    var socketId: String? = null
        private set

    // ── Internal ──────────────────────────────────────────────────────────────

    private val log = Logger.getLogger("AmarWave")

    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "amarwave-scheduler").also { it.isDaemon = true }
    }

    private var webSocket: WebSocket? = null
    private val channels = ConcurrentHashMap<String, Channel>()
    private val retries  = AtomicInteger(0)

    @Volatile private var intentionalClose = false
    @Volatile private var activityTimer: ScheduledFuture<*>? = null
    @Volatile private var pongTimer:     ScheduledFuture<*>? = null
    @Volatile private var reconnectTimer: ScheduledFuture<*>? = null

    private val resolvedWsBase: String
    private val resolvedApiBase: String

    init {
        val clusterKey = config.cluster.lowercase()
        val cluster    = CLUSTERS[clusterKey] ?: CLUSTERS["default"]!!
        val useTLS     = config.forceTLS

        resolvedWsBase = when {
            config.wsHost != null -> {
                val port = if (useTLS) config.wssPort else config.wsPort
                "${if (useTLS) "wss" else "ws"}://${config.wsHost}:$port"
            }
            else -> if (useTLS) cluster.wss else cluster.ws
        }

        resolvedApiBase = when {
            config.apiHost != null -> "http${if (useTLS) "s" else ""}://${config.apiHost}:${config.apiPort}"
            else -> cluster.api
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open the WebSocket connection. No-op if already connecting/connected.
     * Returns `this` for chaining.
     */
    fun connect(): AmarWave {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return this
        intentionalClose = false
        openSocket()
        return this
    }

    /**
     * Close the connection. Auto-reconnect will NOT fire after this.
     * Returns `this` for chaining.
     */
    fun disconnect(): AmarWave {
        intentionalClose = true
        clearTimers()
        webSocket?.close(1000, "user")
        setState(ConnectionState.DISCONNECTED)
        return this
    }

    /**
     * Subscribe to a channel. Auto-connects if needed.
     * Returns a [Channel] immediately — safe to [Channel.bind] at once.
     *
     * @param channelName Channel name, e.g. "chat-room", "private-orders", "presence-lobby"
     */
    fun subscribe(channelName: String): Channel {
        channels[channelName]?.let { return it }
        val ch = Channel(channelName, this)
        channels[channelName] = ch
        if (state == ConnectionState.CONNECTED) {
            doSubscribe(ch)
        } else {
            connect()
        }
        return ch
    }

    /**
     * Unsubscribe from a channel.
     * Returns `this` for chaining.
     */
    fun unsubscribe(channelName: String): AmarWave {
        channels[channelName] ?: return this
        rawSend(mapOf("event" to "amarwave:unsubscribe", "data" to mapOf("channel" to channelName)))
        channels.remove(channelName)
        return this
    }

    /**
     * Get an already-subscribed channel by name. Returns null if not subscribed.
     */
    fun channel(channelName: String): Channel? = channels[channelName]

    /**
     * Publish an event via HTTP POST to /api/v1/trigger.
     * Runs synchronously — call from a background thread or wrap in a coroutine.
     *
     * @return `true` if the server accepted the event.
     */
    fun publish(channel: String, event: String, data: Any? = null): Boolean {
        val body = JSONObject().apply {
            put("app_key",    config.appKey)
            put("app_secret", config.appSecret)
            put("channel",    channel)
            put("name",       event)
            put("data",       if (data == null) JSONObject.NULL else data)
        }.toString()

        return try {
            val req = Request.Builder()
                .url("$resolvedApiBase/api/v1/trigger")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            log.warning("[AmarWave] publish error: ${e.message}")
            false
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private fun buildWsUrl(): String {
        val url = "$resolvedWsBase/ws?app_key=${config.appKey}"
        log.fine("[AmarWave] WS URL: $url")
        return url
    }

    private fun openSocket() {
        setState(ConnectionState.CONNECTING)
        val req = Request.Builder()
            .url(buildWsUrl())
            .addHeader("Origin",     "https://amarwave.com")
            .addHeader("User-Agent", "AmarWave-Kotlin/1.0")
            .build()
        webSocket = http.newWebSocket(req, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            log.fine("[AmarWave] WebSocket opened")
            // wait for connection_established message
        }

        override fun onMessage(ws: WebSocket, text: String) {
            resetActivityTimer()
            handleRawMessage(text)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            log.warning("[AmarWave] WS failure: ${t.message}")
            emit("error", t)
            connection.fireError(t)
            onClose(wasError = true)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            log.fine("[AmarWave] WS closed: $code $reason")
            onClose(wasError = false)
        }
    }

    private fun handleRawMessage(raw: String) {
        val obj = try { JSONObject(raw) } catch (_: Exception) { return }
        val event   = obj.optString("event")
        val channel = obj.optString("channel")
        val data    = parseData(obj.opt("data"))

        when (event) {
            "amarwave:connection_established" -> {
                val dataObj = data as? Map<*, *>
                socketId    = dataObj?.get("socket_id") as? String
                retries.set(0)
                setState(ConnectionState.CONNECTED)
                log.info("[AmarWave] Connected — socket_id=$socketId")
                channels.values.forEach { ch ->
                    ch.subscribed = false
                    doSubscribe(ch)
                }
            }

            "amarwave:error" -> {
                val msg = when (val d = data) {
                    is Map<*, *> -> d["message"] as? String ?: d.toString()
                    else         -> d?.toString() ?: "Server error"
                }
                log.warning("[AmarWave] Server error: $msg")
                emit("error", RuntimeException(msg))
                connection.fireError(RuntimeException(msg))
            }

            "amarwave:pong" -> {
                clearPongTimer()
            }

            "amarwave_internal:subscription_succeeded" -> {
                channels[channel]?.let { ch ->
                    ch.subscribed = true
                    ch.fireEvent("subscribed", data)
                    ch.flushQueue()
                    log.info("[AmarWave] Subscribed → $channel")
                }
            }

            "amarwave_internal:subscription_error" -> {
                channels[channel]?.fireEvent("error", data)
                log.warning("[AmarWave] Subscription error on $channel: $data")
            }

            else -> {
                if (channel.isNotEmpty()) {
                    channels[channel]?.fireEvent(event, data)
                }
                emit(event, mapOf("channel" to channel, "data" to data))
            }
        }
    }

    private fun parseData(raw: Any?): Any? = when (raw) {
        null, JSONObject.NULL -> null
        is String -> try {
            val obj = JSONObject(raw)
            jsonObjectToMap(obj)
        } catch (_: Exception) { raw }
        is JSONObject -> jsonObjectToMap(raw)
        else -> raw
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { key ->
            map[key] = when (val v = obj.get(key)) {
                JSONObject.NULL -> null
                is JSONObject   -> jsonObjectToMap(v)
                else            -> v
            }
        }
        return map
    }

    // ── Subscribe ─────────────────────────────────────────────────────────────

    internal fun doSubscribe(ch: Channel) {
        val name = ch.name
        val data = mutableMapOf<String, Any>("channel" to name)

        try {
            when {
                // ── Client-side HMAC (appSecret provided) ───────────────────
                name.startsWith("presence-") && config.appSecret.isNotEmpty() -> {
                    val channelData = JSONObject()
                        .put("user_id",   uid())
                        .put("user_info", JSONObject())
                        .toString()
                    val sig = hmacSha256(config.appSecret, "${socketId}:${name}:${channelData}")
                    data["auth"]         = "${config.appKey}:$sig"
                    data["channel_data"] = channelData
                }
                name.startsWith("private-") && config.appSecret.isNotEmpty() -> {
                    // Signed string: "<socket_id>:<channel_name>"
                    val sig = hmacSha256(config.appSecret, "${socketId}:${name}")
                    data["auth"] = "${config.appKey}:$sig"
                }
                // ── Server-side auth (no appSecret — call authEndpoint) ──────
                name.startsWith("private-") || name.startsWith("presence-") -> {
                    serverAuth(ch, data)
                }
            }
        } catch (e: Exception) {
            ch.fireEvent("error", e.message)
            return
        }

        rawSend(mapOf("event" to "amarwave:subscribe", "data" to data))
    }

    /**
     * Call [AmarWaveConfig.authEndpoint] with `{socket_id, channel_name}` and
     * merge the response fields (e.g. `auth`, `channel_data`) into [data].
     *
     * The server must return JSON like:
     *   `{"auth":"<appKey>:<hmac_sha256_hex>"}`
     *
     * For presence channels it should also include:
     *   `{"auth":"...","channel_data":"{\"user_id\":\"...\",\"user_info\":{}}"}`
     *
     * @throws Exception if the auth endpoint returns a non-2xx response.
     */
    private fun serverAuth(ch: Channel, data: MutableMap<String, Any>) {
        val body = JSONObject()
            .put("socket_id",    socketId)
            .put("channel_name", ch.name)
            .toString()

        val reqBuilder = Request.Builder()
            .url(config.authEndpoint)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        config.authHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        val resp = http.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) {
            throw Exception("Auth endpoint returned ${resp.code} for channel '${ch.name}'")
        }
        val respJson = JSONObject(resp.body?.string() ?: throw Exception("Empty auth response"))
        respJson.keys().forEach { key -> data[key] = respJson.get(key) }
    }

    // ── Timers ────────────────────────────────────────────────────────────────

    private fun resetActivityTimer() {
        clearActivityTimer()
        activityTimer = scheduler.schedule({
            rawSend(mapOf("event" to "amarwave:ping", "data" to emptyMap<String, Any>()))
            pongTimer = scheduler.schedule({
                log.warning("[AmarWave] Pong timeout — reconnecting")
                webSocket?.close(1001, "pong timeout")
            }, config.pongTimeout, TimeUnit.MILLISECONDS)
        }, config.activityTimeout, TimeUnit.MILLISECONDS)
    }

    private fun clearActivityTimer() {
        activityTimer?.cancel(false)
        activityTimer = null
    }

    private fun clearPongTimer() {
        pongTimer?.cancel(false)
        pongTimer = null
    }

    private fun clearTimers() {
        clearActivityTimer()
        clearPongTimer()
        reconnectTimer?.cancel(false)
        reconnectTimer = null
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun onClose(wasError: Boolean) {
        clearTimers()
        socketId = null
        channels.values.forEach { it.subscribed = false }
        if (intentionalClose) {
            setState(ConnectionState.DISCONNECTED)
            return
        }
        setState(ConnectionState.DISCONNECTED)
        val maxRetries = config.maxRetries
        if (maxRetries > 0 && retries.get() >= maxRetries) {
            log.warning("[AmarWave] Max reconnect attempts reached")
            return
        }
        val delay = minOf(
            config.reconnectDelay * (1L shl retries.getAndIncrement()),
            config.maxReconnectDelay
        )
        log.info("[AmarWave] Reconnecting in ${delay}ms (attempt ${retries.get()})…")
        reconnectTimer = scheduler.schedule({ openSocket() }, delay, TimeUnit.MILLISECONDS)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun rawSend(payload: Map<String, Any?>) {
        val ws = webSocket ?: return
        if (ws.send(JSONObject(payload).toString())) return
        log.warning("[AmarWave] Send failed — socket may be closing")
    }

    private fun setState(newState: ConnectionState) {
        state = newState
        emit(newState.name.lowercase())
        connection.fireState(newState)
    }

    /**
     * Shutdown the internal OkHttp dispatcher and scheduler.
     * Call this when your app/component is destroyed to free resources.
     */
    fun shutdown() {
        disconnect()
        scheduler.shutdown()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
