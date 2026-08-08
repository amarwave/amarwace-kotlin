package io.amarwave

/** Current state of the WebSocket connection. */
enum class ConnectionState {
    INITIALIZED, CONNECTING, CONNECTED, DISCONNECTED
}

/** SDK configuration passed to [AmarWave]. */
data class AmarWaveConfig(
    /** Your app's public key. */
    val appKey: String,
    /** Your app's secret — used for HMAC auth on private/presence channels. */
    val appSecret: String = "",
    /** Named cluster: "default", "eu", "us", "ap1", "ap2", "local". */
    val cluster: String = "default",
    /** Override WebSocket hostname (skips cluster resolution when set). */
    val wsHost: String? = null,
    /** Plain-WebSocket port. Default: 80. */
    val wsPort: Int = 80,
    /** TLS WebSocket port. Default: 443. */
    val wssPort: Int = 443,
    /** Override API hostname. */
    val apiHost: String? = null,
    /** API port. Default: 8000. */
    val apiPort: Int = 8000,
    /** Use wss:// instead of ws://. Always set true for production. */
    val forceTLS: Boolean = false,
    /** Server auth endpoint for private/presence channels (server-side auth mode). */
    val authEndpoint: String = "/broadcasting/auth",
    /** Headers sent with server-auth requests. */
    val authHeaders: Map<String, String> = emptyMap(),
    /** Initial reconnect delay in ms. Doubles on each failure (exponential back-off). */
    val reconnectDelay: Long = 1_000L,
    /** Maximum reconnect delay in ms. */
    val maxReconnectDelay: Long = 30_000L,
    /** Maximum reconnect attempts. 0 = unlimited. */
    val maxRetries: Int = 5,
    /** Inactivity timeout before a ping is sent (ms). */
    val activityTimeout: Long = 120_000L,
    /** Time to wait for a pong before reconnecting (ms). */
    val pongTimeout: Long = 30_000L,
)

/** @internal Resolved cluster URLs. */
internal data class ClusterConfig(
    val wss: String,
    val ws:  String,
    val api: String,
)

/** A single event registered with [Channel.bind]. */
internal typealias EventHandler = (data: Any?) -> Unit
