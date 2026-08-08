package io.amarwave

/**
 * Pusher-compatible connection proxy exposed as [AmarWave.connection].
 * Mirrors the parent's state and socket_id. Bind connection lifecycle events here.
 *
 * ```kotlin
 * aw.connection.bind("connected")    { Log.d("AW", "Connected: ${aw.connection.socketId}") }
 * aw.connection.bind("disconnected") { Log.d("AW", "Disconnected") }
 * aw.connection.bind("error")        { err -> Log.e("AW", err.toString()) }
 * ```
 */
class Connection internal constructor(
    private val getSocketId: () -> String?,
) : EventEmitter() {

    /** Current connection state. */
    var state: ConnectionState = ConnectionState.INITIALIZED
        internal set

    /** The socket ID assigned by the server. Null when disconnected. */
    val socketId: String? get() = getSocketId()

    internal fun fireState(newState: ConnectionState, data: Any? = null) {
        state = newState
        emit(newState.name.lowercase(), data)
    }

    internal fun fireError(error: Throwable) {
        emit("error", error)
    }
}
