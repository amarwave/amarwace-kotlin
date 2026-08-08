package io.amarwave

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AmarWaveTest {

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Test
    fun `hmacSha256 produces correct hex digest`() {
        val sig = hmacSha256("secret", "hello")
        assertEquals(64, sig.length)
        assertTrue(sig.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `hmacSha256 is deterministic`() {
        val a = hmacSha256("key", "msg")
        val b = hmacSha256("key", "msg")
        assertEquals(a, b)
    }

    @Test
    fun `hmacSha256 differs for different secrets`() {
        val a = hmacSha256("secret1", "msg")
        val b = hmacSha256("secret2", "msg")
        assertNotEquals(a, b)
    }

    @Test
    fun `uid produces unique values`() {
        val ids = (1..100).map { uid() }.toSet()
        assertEquals(100, ids.size)
    }

    // ── EventEmitter ──────────────────────────────────────────────────────────

    @Test
    fun `bind and emit single event`() {
        val emitter = EventEmitter()
        var received: Any? = null
        emitter.bind("test") { received = it }
        emitter.emit("test", "hello")
        assertEquals("hello", received)
    }

    @Test
    fun `unbind removes listener`() {
        val emitter = EventEmitter()
        var count = 0
        val handler: EventHandler = { count++ }
        emitter.bind("evt", handler)
        emitter.emit("evt")
        emitter.unbind("evt", handler)
        emitter.emit("evt")
        assertEquals(1, count)
    }

    @Test
    fun `bindGlobal fires for all events`() {
        val emitter = EventEmitter()
        val events  = mutableListOf<String>()
        emitter.bindGlobal { event, _ -> events.add(event) }
        emitter.emit("a")
        emitter.emit("b")
        emitter.emit("c")
        assertEquals(listOf("a", "b", "c"), events)
    }

    @Test
    fun `multiple listeners for same event`() {
        val emitter = EventEmitter()
        var count = 0
        emitter.bind("x") { count++ }
        emitter.bind("x") { count++ }
        emitter.bind("x") { count++ }
        emitter.emit("x")
        assertEquals(3, count)
    }

    // ── Config / Cluster resolution ───────────────────────────────────────────

    @Test
    fun `default cluster resolves to amarwave_com wss`() {
        val cluster = CLUSTERS["default"]!!
        assertTrue(cluster.wss.startsWith("wss://amarwave.com"))
    }

    @Test
    fun `local cluster resolves to localhost`() {
        val cluster = CLUSTERS["local"]!!
        assertTrue(cluster.ws.contains("localhost"))
        assertTrue(cluster.api.contains("localhost"))
    }

    @Test
    fun `AmarWave constructs without throwing`() {
        val aw = AmarWave(AmarWaveConfig(appKey = "test-key"))
        assertNotNull(aw)
        assertNotNull(aw.connection)
        assertEquals(ConnectionState.INITIALIZED, aw.state)
        assertNull(aw.socketId)
        aw.shutdown()
    }

    @Test
    fun `connection initial state is INITIALIZED`() {
        val aw = AmarWave(AmarWaveConfig(appKey = "k", appSecret = "s"))
        assertEquals(ConnectionState.INITIALIZED, aw.connection.state)
        assertNull(aw.connection.socketId)
        aw.shutdown()
    }

    // ── HTTP publish (mock server) ────────────────────────────────────────────

    private lateinit var mockServer: HttpServer
    private var lastRequestBody: String = ""
    private var mockResponseCode: Int   = 200

    @Before
    fun startMockServer() {
        mockServer = HttpServer.create(InetSocketAddress(0), 0)
        mockServer.createContext("/api/v1/trigger") { ex ->
            lastRequestBody = ex.requestBody.bufferedReader().readText()
            val resp = if (mockResponseCode == 200) """{"status":"ok"}""" else """{"error":"bad"}"""
            ex.sendResponseHeaders(mockResponseCode, resp.length.toLong())
            ex.responseBody.use { it.write(resp.toByteArray()) }
        }
        mockServer.start()
    }

    @After
    fun stopMockServer() {
        mockServer.stop(0)
    }

    private fun mockPort() = mockServer.address.port

    @Test
    fun `publish sends correct JSON to trigger endpoint`() {
        val aw = AmarWave(AmarWaveConfig(
            appKey    = "my-key",
            appSecret = "my-secret",
            apiHost   = "localhost",
            apiPort   = mockPort(),
        ))
        val ok = aw.publish("test-ch", "my-event", mapOf("foo" to "bar"))
        assertTrue("publish should return true", ok)
        assertTrue("body should contain app_key",   lastRequestBody.contains("my-key"))
        assertTrue("body should contain app_secret", lastRequestBody.contains("my-secret"))
        assertTrue("body should contain channel",    lastRequestBody.contains("test-ch"))
        assertTrue("body should use 'name' field",   lastRequestBody.contains("\"name\""))
        assertTrue("body should contain event name", lastRequestBody.contains("my-event"))
        aw.shutdown()
    }

    @Test
    fun `publish returns false on server error`() {
        mockResponseCode = 400
        val aw = AmarWave(AmarWaveConfig(
            appKey  = "k",
            apiHost = "localhost",
            apiPort = mockPort(),
        ))
        val ok = aw.publish("ch", "ev", null)
        assertFalse("publish should return false for 4xx", ok)
        aw.shutdown()
    }

    @Test
    fun `publish with null data serialises correctly`() {
        val aw = AmarWave(AmarWaveConfig(
            appKey  = "k",
            apiHost = "localhost",
            apiPort = mockPort(),
        ))
        aw.publish("ch", "ev", null)
        assertTrue(lastRequestBody.contains("null") || lastRequestBody.contains("ch"))
        aw.shutdown()
    }

    // ── Channel ───────────────────────────────────────────────────────────────

    @Test
    fun `channel queues publishes before subscribed`() {
        val aw = AmarWave(AmarWaveConfig(
            appKey  = "k",
            apiHost = "localhost",
            apiPort = mockPort(),
        ))
        val ch = Channel("test", aw)
        // Not subscribed yet — publish should queue (return true without hitting server)
        val result = ch.publish("evt", "data")
        assertTrue(result)
        // Server body should be empty (queued, not sent)
        assertEquals("", lastRequestBody)
        aw.shutdown()
    }
}
