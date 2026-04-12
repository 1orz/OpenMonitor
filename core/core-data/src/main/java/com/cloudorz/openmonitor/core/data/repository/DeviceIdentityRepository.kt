package com.cloudorz.openmonitor.core.data.repository

import android.content.Context
import android.util.Log
import com.cloudorz.openmonitor.core.data.datasource.DeviceFingerprintCollector
import com.cloudorz.openmonitor.core.data.util.ApiEncryptor
import com.cloudorz.openmonitor.core.data.util.ApiResponseParser
import com.cloudorz.openmonitor.core.data.util.ApiSigner
import com.cloudorz.openmonitor.core.model.identity.DeviceFingerprint
import com.cloudorz.openmonitor.core.model.identity.DeviceIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备身份仓库：采集指纹 → POST /api/identify → 缓存 UUID。
 *
 * 两层识别策略（后端实现）：
 * 1. 客户端带上缓存 UUID → 后端查记录并校验 DRM 吻合 → 确认身份
 * 2. 无缓存 UUID（清除数据/重装）→ 后端通过 media_drm_id 唯一匹配找回
 * 3. 均无法匹配 → 创建新设备记录
 */
@Singleton
class DeviceIdentityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collector: DeviceFingerprintCollector,
) {
    companion object {
        private const val TAG = "DeviceIdentity"
        private const val PREFS_NAME = "device_identity"
        private const val KEY_UUID = "uuid"
        private const val KEY_IS_NEW = "is_new"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_LAST_IDENTIFIED_AT = "last_identified_at"
        private const val IDENTIFY_URL = "https://om-api.cloudorz.com/api/v1/identify"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 返回缓存的设备身份，未识别过则返回 null */
    fun getCachedIdentity(): DeviceIdentity? {
        val uuid = prefs.getString(KEY_UUID, null) ?: return null
        return DeviceIdentity(
            uuid = uuid,
            isNew = prefs.getBoolean(KEY_IS_NEW, false),
            createdAt = prefs.getLong(KEY_CREATED_AT, 0L),
            lastIdentifiedAt = prefs.getLong(KEY_LAST_IDENTIFIED_AT, 0L),
        )
    }

    /** 返回缓存的 UUID，如果没有则进行网络识别 */
    suspend fun getOrCreateIdentity(): Result<DeviceIdentity> {
        val cached = getCachedIdentity()
        if (cached != null) return Result.success(cached)
        return identify()
    }

    /** 采集指纹并发送到服务端进行识别，返回设备身份 */
    suspend fun identify(): Result<DeviceIdentity> = withContext(Dispatchers.IO) {
        try {
            val fingerprint = collector.collect()
            val cachedUuid = prefs.getString(KEY_UUID, null)
            val identity = postIdentify(fingerprint, cachedUuid)
            cacheIdentity(identity)
            Result.success(identity)
        } catch (e: Exception) {
            Log.w(TAG, "identify failed", e)
            Result.failure(e)
        }
    }

    /** 获取最近一次采集的指纹（仅采集，不发送网络请求） */
    suspend fun collectFingerprint(): DeviceFingerprint {
        return collector.collect()
    }

    private fun postIdentify(fingerprint: DeviceFingerprint, cachedUuid: String?): DeviceIdentity {
        val conn = URL(IDENTIFY_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = fingerprint.toJson()
            if (cachedUuid != null) {
                body.put("cached_uuid", cachedUuid)
            }
            val encrypted = ApiEncryptor.encrypt(body.toString())

            ApiSigner.sign("POST", "/api/v1/identify", encrypted).applyTo(conn)
            conn.outputStream.bufferedWriter().use { it.write(encrypted) }

            val code = conn.responseCode
            if (code != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "identify failed: HTTP $code, body=$errorBody")
                throw IdentifyException("HTTP $code: $errorBody")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            Log.i(TAG, "identify response: $response")
            parseIdentityResponse(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseIdentityResponse(response: String): DeviceIdentity {
        val data = ApiResponseParser.unwrapData(response)
        val uuid = data.getString("uuid")
        if (uuid.isBlank()) throw IdentifyException("Empty uuid in response")
        return DeviceIdentity(
            uuid = uuid,
            isNew = data.optBoolean("is_new", false),
            createdAt = data.optLong("created_at", 0L),
            lastIdentifiedAt = System.currentTimeMillis(),
        )
    }

    private fun cacheIdentity(identity: DeviceIdentity) {
        prefs.edit()
            .putString(KEY_UUID, identity.uuid)
            .putBoolean(KEY_IS_NEW, identity.isNew)
            .putLong(KEY_CREATED_AT, identity.createdAt)
            .putLong(KEY_LAST_IDENTIFIED_AT, identity.lastIdentifiedAt)
            .apply()
    }

    /** 清除本地缓存（用于调试或重新识别） */
    fun clearCache() {
        prefs.edit().clear().apply()
    }
}

class IdentifyException(message: String) : Exception(message)
