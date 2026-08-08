package io.amarwave

/**
 * Represents a subscribed AmarWave channel.
 *
 * Obtained via [AmarWave.subscribe]. Safe to call [bind] and [publish] immediately —
 * publishes are queued until the channel is confirmed subscribed.
 *
 * ```kotlin
 * val ch = aw.subscribe("chat-room")
 *
 * ch.bind("subscribed") { Log.d("AW", "Ready!") }
 * ch.bind("message")    { data -> println(data) }
 *
 * ch.publish("message", mapOf("user" to "Ali", "text" to "Hello!"))
 * ```
 */
class Channel internal constructor(
    /** The channel name (e.g. "chat-room", "private-orders"). */
    val name: String,
    private val aw: AmarWave,
) : EventEmitter() {

    /** True once the server confirms subscription. */
    @Volatile var subscribed: Boolean = false
        internal set

    private data class QueuedPublish(val event: String, val data: Any?)
    private val queue = ArrayDeque<QueuedPublish>()

    /**
     * Publish an event on this channel via HTTP.
     * If the channel is not yet subscribed, the publish is queued and sent once confirmed.
     *
     * @return `true` if the event was accepted by the server, `false` on error.
     */
    fun publish(event: String, data: Any? = null): Boolean {
        if (!subscribed) {
            queue.addLast(QueuedPublish(event, data))
            return true
        }
        return aw.publish(name, event, data)
    }

    /** Alias for [publish] — Pusher-compatible name. */
    fun trigger(event: String, data: Any? = null): Boolean = publish(event, data)

    internal fun fireEvent(event: String, data: Any?) = emit(event, data)

    internal fun flushQueue() {
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            aw.publish(name, q.event, q.data)
        }
    }
}
