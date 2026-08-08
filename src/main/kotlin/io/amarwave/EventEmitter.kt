package io.amarwave

import java.util.concurrent.CopyOnWriteArrayList

/** Thread-safe event emitter base. */
open class EventEmitter {

    private val listeners = HashMap<String, CopyOnWriteArrayList<EventHandler>>()
    private val globalListeners = CopyOnWriteArrayList<(event: String, data: Any?) -> Unit>()

    /**
     * Register a listener for [event].
     * Returns `this` for chaining.
     */
    fun bind(event: String, handler: EventHandler): EventEmitter {
        listeners.getOrPut(event) { CopyOnWriteArrayList() }.add(handler)
        return this
    }

    /** Alias for [bind]. */
    fun on(event: String, handler: EventHandler): EventEmitter = bind(event, handler)

    /**
     * Remove a specific listener for [event], or all listeners if [handler] is null.
     */
    fun unbind(event: String, handler: EventHandler? = null): EventEmitter {
        if (handler == null) {
            listeners.remove(event)
        } else {
            listeners[event]?.remove(handler)
        }
        return this
    }

    /** Alias for [unbind]. */
    fun off(event: String, handler: EventHandler? = null): EventEmitter = unbind(event, handler)

    /**
     * Register a listener that fires for EVERY event on this emitter.
     */
    fun bindGlobal(handler: (event: String, data: Any?) -> Unit): EventEmitter {
        globalListeners.add(handler)
        return this
    }

    /** Remove a global listener. */
    fun unbindGlobal(handler: ((event: String, data: Any?) -> Unit)? = null): EventEmitter {
        if (handler == null) globalListeners.clear() else globalListeners.remove(handler)
        return this
    }

    /** @internal Fire an event to all registered listeners. */
    internal fun emit(event: String, data: Any? = null) {
        listeners[event]?.forEach { it.invoke(data) }
        globalListeners.forEach { it.invoke(event, data) }
    }
}
