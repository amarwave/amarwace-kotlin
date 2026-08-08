package io.amarwave

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.UUID

/**
 * HMAC-SHA256 using the JVM's built-in javax.crypto.
 * No third-party dependency required. Works on Android and JVM.
 */
internal fun hmacSha256(secret: String, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Generate a random socket / user ID. */
internal fun uid(): String = UUID.randomUUID().toString().replace("-", "")
