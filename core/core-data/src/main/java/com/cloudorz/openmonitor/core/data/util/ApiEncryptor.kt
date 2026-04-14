package com.cloudorz.openmonitor.core.data.util

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiEncryptor {

    var serverPublicKeyBase64: String = ""

    private val serverPublicKey: ECPublicKey by lazy {
        val bytes = Base64.decode(serverPublicKeyBase64, Base64.NO_WRAP)
        KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(bytes)) as ECPublicKey
    }

    fun encrypt(plaintext: String): String {
        if (serverPublicKeyBase64.isEmpty()) return plaintext

        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeral = keyGen.generateKeyPair()

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(serverPublicKey, true)
        val shared = agreement.generateSecret()

        val aesKeyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(shared)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ek = Base64.encodeToString(ephemeral.public.encoded, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)

        return JSONObject().apply {
            put("ek", ek)
            put("iv", ivB64)
            put("ct", ctB64)
        }.toString()
    }
}
