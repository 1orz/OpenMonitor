package com.cloudorz.openmonitor.core.data.util

import android.util.Base64
import com.cloudorz.openmonitor.core.model.identity.ActivationPlan
import com.cloudorz.openmonitor.core.model.identity.ActivationState
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Ed25519 激活令牌验证器（纯函数，无副作用）。
 *
 * Token 格式 (97 bytes = payload 33 + signature 64):
 *   payload:
 *     [0..15]  uuid_sha  — SHA-256(uuid)[0:16]，设备绑定
 *     [16]     plan      — ActivationPlan.code
 *     [17..24] expiresAt — 8 bytes big-endian long (0=永久)
 *     [25..32] activatedAt — 8 bytes big-endian long
 *   signature:
 *     [33..96] Ed25519 signature over payload
 */
object ActivationTokenVerifier {

    private const val PAYLOAD_SIZE = 33
    private const val SIGNATURE_SIZE = 64
    private const val TOKEN_SIZE = PAYLOAD_SIZE + SIGNATURE_SIZE
    private const val UUID_HASH_SIZE = 16

    var publicKeyBase64: String = ""

    private val publicKey: EdDSAPublicKey by lazy {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        // X.509 SubjectPublicKeyInfo DER: last 32 bytes are the raw Ed25519 public key
        val rawKey = keyBytes.takeLast(32).toByteArray()
        val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        EdDSAPublicKey(EdDSAPublicKeySpec(rawKey, spec))
    }

    fun verify(uuid: String, tokenBase64: String): ActivationState? {
        if (publicKeyBase64.isEmpty() || tokenBase64.isEmpty()) return null

        val raw = try {
            Base64.decode(tokenBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
        if (raw.size != TOKEN_SIZE) return null

        val payload = raw.copyOfRange(0, PAYLOAD_SIZE)
        val signature = raw.copyOfRange(PAYLOAD_SIZE, TOKEN_SIZE)

        // 1. Ed25519 验签
        if (!verifySignature(payload, signature)) return null

        // 2. UUID 绑定校验
        val expectedHash = sha256(uuid.toByteArray(Charsets.UTF_8)).copyOfRange(0, UUID_HASH_SIZE)
        val actualHash = payload.copyOfRange(0, UUID_HASH_SIZE)
        if (!expectedHash.contentEquals(actualHash)) return null

        // 3. 解析 payload
        val plan = payload[16].toInt() and 0xFF
        val buf = ByteBuffer.wrap(payload)
        val expiresAt = buf.getLong(17)
        val activatedAt = buf.getLong(25)

        // 4. 有效期检查
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) return null

        return ActivationState(
            activated = true,
            plan = ActivationPlan.fromCode(plan),
            activatedAt = activatedAt,
            expiresAt = expiresAt,
            token = tokenBase64,
        )
    }

    private fun verifySignature(payload: ByteArray, signature: ByteArray): Boolean {
        return try {
            val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(publicKey)
            engine.update(payload)
            engine.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
