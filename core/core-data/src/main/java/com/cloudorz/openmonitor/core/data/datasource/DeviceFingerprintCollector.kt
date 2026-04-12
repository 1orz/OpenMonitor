package com.cloudorz.openmonitor.core.data.datasource

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.MediaDrm
import android.os.Build
import android.util.Base64
import android.util.Log
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.model.identity.DeviceFingerprint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 采集设备指纹数据：MediaDrm Widevine ID、Build 属性、硬件特征、ro.serialno。
 */
@Singleton
class DeviceFingerprintCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor,
    private val permissionManager: PermissionManager,
) {
    companion object {
        private const val TAG = "FingerprintCollector"
        private const val WIDEVINE_UUID_MSB = -0x121074568629B532L
        private const val WIDEVINE_UUID_LSB = -0x5C37D8232AE2DE13L
        private const val MEDIA_DRM_TIMEOUT_MS = 3_000L
    }

    suspend fun collect(): DeviceFingerprint = withContext(Dispatchers.IO) {
        DeviceFingerprint(
            mediaDrmId = getMediaDrmId(),
            serialNo = getSerialNo(),
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            socModel = getSocModel(),
            socManufacturer = getSocManufacturer(),
            screenWidth = getScreenWidth(),
            screenHeight = getScreenHeight(),
            screenDensity = getScreenDensity(),
            totalRam = getTotalRam(),
            sensorHash = getSensorHash(),
            sdkInt = Build.VERSION.SDK_INT,
            privilegeMode = permissionManager.currentMode.value.name,
        )
    }

    /**
     * 获取 MediaDrm Widevine ID 的 SHA-256 哈希。
     * 硬件级标识，恢复出厂设置不变。API 28+ 可用。
     */
    private suspend fun getMediaDrmId(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return withTimeoutOrNull(MEDIA_DRM_TIMEOUT_MS) {
            try {
                val widevineUuid = UUID(WIDEVINE_UUID_MSB, WIDEVINE_UUID_LSB)
                val drm = MediaDrm(widevineUuid)
                try {
                    val rawId = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                    val rawBase64 = Base64.encodeToString(rawId, Base64.NO_WRAP)
                    sha256Hex(rawBase64.toByteArray())
                } finally {
                    drm.close()
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaDrm unavailable", e)
                null
            }
        }
    }

    /**
     * 通过 shell 获取 ro.serialno（主板序列号）。
     * 需要 ADB/Root/Shizuku 权限，BASIC 模式下可能返回 null。
     */
    private suspend fun getSerialNo(): String? {
        return try {
            val result = shellExecutor.execute("getprop ro.serialno")
            val serial = result.stdout.trim()
            serial.ifEmpty { null }?.takeIf { it != "unknown" }
        } catch (e: Exception) {
            Log.d(TAG, "getSerialNo failed", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getScreenWidth(): Int {
        val dm = context.resources.displayMetrics
        return dm.widthPixels
    }

    @Suppress("DEPRECATION")
    private fun getScreenHeight(): Int {
        val dm = context.resources.displayMetrics
        return dm.heightPixels
    }

    private fun getScreenDensity(): Int {
        return context.resources.displayMetrics.densityDpi
    }

    private fun getTotalRam(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0L
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    /**
     * 传感器列表签名：按名称排序后拼接再哈希。
     * 同型号设备传感器列表通常相同，作为辅助指纹。
     */
    private fun getSensorHash(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return ""
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val names = sensors.map { it.name }.sorted().joinToString(",")
        return sha256Hex(names.toByteArray())
    }

    private fun getSocModel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
    }

    private fun getSocManufacturer(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }
}
