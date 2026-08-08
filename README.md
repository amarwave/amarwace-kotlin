# AmarWave Kotlin SDK

Real-time WebSocket client for [AmarWave](https://amarwave.com) — works on Android and JVM.

[![JitPack](https://jitpack.io/v/amarwave/amarwace-kotlin.svg)](https://jitpack.io/#amarwave/amarwace-kotlin)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Installation

### Step 1 — Add JitPack repository

**Gradle (settings.gradle.kts)**
```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

**Gradle (settings.gradle — Groovy)**
```groovy
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2 — Add the dependency

**build.gradle.kts**
```kotlin
dependencies {
    implementation("com.github.amarwave:amarwace-kotlin:1.0.0")
}
```

**build.gradle (Groovy)**
```groovy
dependencies {
    implementation 'com.github.amarwave:amarwace-kotlin:1.0.0'
}
```

> **Android** — add `INTERNET` permission to `AndroidManifest.xml`:
> ```xml
> <uses-permission android:name="android.permission.INTERNET" />
> ```

---

## Quick Start

```kotlin
import io.amarwave.AmarWave
import io.amarwave.AmarWaveConfig

val aw = AmarWave(AmarWaveConfig(
    appKey    = "YOUR_APP_KEY",
    appSecret = "YOUR_APP_SECRET",
    cluster   = "default",
    forceTLS  = true,   // always true for production
))

// Connection events
aw.connection.bind("connected")    { Log.d("AW", "Connected: ${aw.connection.socketId}") }
aw.connection.bind("disconnected") { Log.d("AW", "Disconnected") }
aw.connection.bind("error")        { err -> Log.e("AW", err.toString()) }

// Subscribe to a channel
val channel = aw.subscribe("chat-room")

channel.bind("subscribed") {
    Log.d("AW", "Ready!")
    // Publish once subscribed
    Thread { channel.publish("message", mapOf("user" to "Ali", "text" to "Hello!")) }.start()
}

channel.bind("message") { data ->
    Log.d("AW", "Message: $data")
}
```

---

## Android Activity / Fragment Integration

Always call `shutdown()` when the Activity or Fragment is destroyed to release resources.

```kotlin
class ChatActivity : AppCompatActivity() {

    private lateinit var aw: AmarWave
    private lateinit var channel: Channel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aw = AmarWave(AmarWaveConfig(
            appKey   = BuildConfig.AMARWAVE_APP_KEY,
            cluster  = "default",
            forceTLS = true,
        ))

        channel = aw.subscribe("chat-room")

        channel.bind("message") { data ->
            runOnUiThread {
                // update UI
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aw.shutdown()   // releases threads and connections
    }
}
```

---

## Channel Types

### Public channel
```kotlin
val ch = aw.subscribe("chat-room")
```

### Private channel (HMAC auth with appSecret)
```kotlin
val ch = aw.subscribe("private-orders")
// SDK signs automatically using your appSecret
```

### Presence channel (membership tracking)
```kotlin
val ch = aw.subscribe("presence-lobby")
```

### Server-side auth (without appSecret on client)
```kotlin
val aw = AmarWave(AmarWaveConfig(
    appKey       = "YOUR_APP_KEY",
    authEndpoint = "https://your-server.com/broadcasting/auth",
    authHeaders  = mapOf("Authorization" to "Bearer $token"),
))
val ch = aw.subscribe("private-orders")
```

---

## Publishing Events

```kotlin
// Publish runs an HTTP POST — call from a background thread
Thread {
    val ok = aw.publish("chat-room", "message", mapOf(
        "user" to "Ali",
        "text" to "Hello from Kotlin!",
    ))
    Log.d("AW", "Published: $ok")
}.start()

// Or via the channel object
channel.publish("message", mapOf("user" to "Ali", "text" to "Hello!"))
```

---

## Connection Lifecycle

```kotlin
aw.connection.bind("connected")    { /* socket ready */ }
aw.connection.bind("disconnected") { /* offline, auto-reconnect will fire */ }
aw.connection.bind("connecting")   { /* attempting connection */ }
aw.connection.bind("error")        { err -> /* handle error */ }

// Reconnect settings
val aw = AmarWave(AmarWaveConfig(
    appKey          = "KEY",
    reconnectDelay  = 1_000L,   // 1s initial delay
    maxReconnectDelay = 30_000L, // 30s max
    maxRetries      = 5,        // 0 = unlimited
))
```

---

## Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `appKey` | String | (required) | Your application key |
| `appSecret` | String | `""` | Secret for HMAC auth on private/presence channels |
| `cluster` | String | `"default"` | Named cluster — resolves host automatically |
| `forceTLS` | Boolean | `false` | Use `wss://` — **always set `true` for production** |
| `wsHost` | String? | null | Override WebSocket hostname |
| `wsPort` | Int | 80 | WebSocket port (plain) |
| `wssPort` | Int | 443 | WebSocket port (TLS) |
| `apiHost` | String? | null | Override API hostname |
| `apiPort` | Int | 8000 | API port |
| `authEndpoint` | String | `/broadcasting/auth` | Server auth endpoint |
| `reconnectDelay` | Long | 1000 | Initial reconnect delay (ms) |
| `maxReconnectDelay` | Long | 30000 | Max reconnect delay (ms) |
| `maxRetries` | Int | 5 | Max reconnect attempts (0 = unlimited) |
| `activityTimeout` | Long | 120000 | Inactivity timeout before ping (ms) |

## Cluster shortcuts

| Cluster | WebSocket | API |
|---------|-----------|-----|
| `default` | `wss://amarwave.com` | `https://amarwave.com` |
| `eu` | `wss://amarwave.com` | `https://amarwave.com` |
| `us` | `wss://amarwave.com` | `https://amarwave.com` |
| `ap1` | `wss://amarwave.com` | `https://amarwave.com` |
| `ap2` | `wss://amarwave.com` | `https://amarwave.com` |
| `local` | `ws://localhost:3001` | `http://localhost:8000` |

---

## Requirements

- Kotlin 1.9+
- Java 8+ / Android API 21+
- OkHttp 4.x (transitive dependency — no extra setup needed)

## License

MIT © [Mehedi Hasan](https://github.com/fnnaeem1881)
