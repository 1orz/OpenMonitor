package com.cloudorz.openmonitor.core.data.repository

import android.content.Context
import android.util.Log
import com.cloudorz.openmonitor.core.data.util.ActivationTokenVerifier
import com.cloudorz.openmonitor.core.data.util.ApiEncryptor
import com.cloudorz.openmonitor.core.data.util.ApiResponseParser
import com.cloudorz.openmonitor.core.data.util.ApiSigner
import com.cloudorz.openmonitor.core.model.identity.ActivationPlan
import com.cloudorz.openmonitor.core.model.identity.ActivationState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 激活码仓库：在线激活 + 本地缓存 + identify 同步。
 *
 * 流程:
 * 1. 用户输入激活码 → activate(uuid, code) → POST /api/v1/activate
 * 2. 服务端验证后签发 Ed25519 token → 本地缓存
 * 3. 每次 identify() 服务端返回 activation 字段 → 本地同步更新
 * 4. 每次读取缓存时用公钥重新验签 → 防篡改
 */
@Singleton
class ActivationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "Activation"
        private const val PREFS_NAME = "activation"
        private const val KEY_TOKEN = "token"
        private const val KEY_UUID = "uuid"
        private const val ACTIVATE_URL = "https://om-api.cloudorz.com/api/v1/activate"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 读取本地缓存的激活状态，每次都重新验签。
     * 返回 null 表示未激活或 token 无效。
     */
    fun getCachedState(): ActivationState? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val uuid = prefs.getString(KEY_UUID, null) ?: return null
        return ActivationTokenVerifier.verify(uuid, token)
    }

    /** 当前是否已激活 */
    fun isActivated(): Boolean = getCachedState()?.activated == true

    /** 当前激活等级 */
    fun currentPlan(): ActivationPlan = getCachedState()?.plan ?: ActivationPlan.NONE

    /**
     * 在线激活：发送 uuid + code 到服务端，获取签名 token。
     */
    suspend fun activate(uuid: String, code: String): Result<ActivationState> =
        withContext(Dispatchers.IO) {
            try {
                val state = postActivate(uuid, code)
                cacheToken(uuid, state.token)
                Result.success(state)
            } catch (e: Exception) {
                Log.w(TAG, "activate failed", e)
                Result.failure(e)
            }
        }

    /**
     * 从 identify 响应中同步激活状态（由 DeviceIdentityRepository 调用）。
     * activation 为 null 时清除本地缓存（吊销/过期）。
     */
    fun syncFromIdentifyResponse(uuid: String, activationJson: JSONObject?) {
        if (activationJson == null) {
            clearCache()
            return
        }
        val token = activationJson.optString("token", "")
        if (token.isEmpty()) {
            clearCache()
            return
        }
        val state = ActivationTokenVerifier.verify(uuid, token)
        if (state != null) {
            cacheToken(uuid, token)
        } else {
            Log.w(TAG, "Token from identify response failed verification")
            clearCache()
        }
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    private fun postActivate(uuid: String, code: String): ActivationState {
        val conn = URL(ACTIVATE_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("uuid", uuid)
                put("code", code)
            }
            val encrypted = ApiEncryptor.encrypt(body.toString())

            ApiSigner.sign("POST", "/api/v1/activate", encrypted).applyTo(conn)
            conn.outputStream.bufferedWriter().use { it.write(encrypted) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "activate failed: HTTP $responseCode, body=$errorBody")
                throw ActivationException(parseErrorMessage(responseCode, errorBody))
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            parseActivateResponse(uuid, response)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseActivateResponse(uuid: String, response: String): ActivationState {
        val data = ApiResponseParser.unwrapData(response)
        val token = data.getString("token")
        if (token.isBlank()) throw ActivationException("Empty token in response")

        val state = ActivationTokenVerifier.verify(uuid, token)
            ?: throw ActivationException("Server token verification failed")

        return state
    }

    private fun cacheToken(uuid: String, token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_UUID, uuid)
            .apply()
    }

    private fun parseErrorMessage(httpCode: Int, errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            json.optString("message", "HTTP $httpCode")
        } catch (_: Exception) {
            "HTTP $httpCode"
        }
    }
}

class ActivationException(message: String) : Exception(message)
