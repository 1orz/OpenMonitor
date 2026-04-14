package com.cloudorz.openmonitor.core.data.util

import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ApiSigner {

    var hmacKey: String = ""

    private val keyBytes by lazy { hmacKey.toByteArray(Charsets.UTF_8) }

    fun sign(method: String, path: String, body: String?): SignedHeaders {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256Hex(body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))

        val sigString = "${method.uppercase()}\n$path\n$timestamp\n$nonce\n$bodyHash"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val signature = mac.doFinal(sigString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return SignedHeaders(signature, timestamp, nonce)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class SignedHeaders(
    val signature: String,
    val timestamp: String,
    val nonce: String,
) {
    fun applyTo(conn: HttpURLConnection) {
        conn.setRequestProperty("X-Signature", signature)
        conn.setRequestProperty("X-Timestamp", timestamp)
        conn.setRequestProperty("X-Nonce", nonce)
    }
}
