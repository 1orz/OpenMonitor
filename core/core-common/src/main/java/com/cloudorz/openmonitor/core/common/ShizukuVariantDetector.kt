package com.cloudorz.openmonitor.core.common

import android.content.Context
import android.content.pm.PackageManager
import com.elvishew.xlog.XLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether Shizuku (or Sui) is running, its authorization state,
 * the auth method (ADB vs Root), and the server version.
 *
 * Auth method is determined by [Shizuku.getUid]:
 *   - UID 0   → started by root (Sui or Shizuku root-start mode)
 *   - UID 2000 → started via ADB
 */
@Singleton
class ShizukuVariantDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ShizukuVariantDetector"
    }

    enum class AuthMethod { ADB, ROOT }

    data class Result(
        val isRunning: Boolean = false,
        val isAuthorized: Boolean = false,
        val authMethod: AuthMethod? = null,
        val version: String? = null,   // full version string e.g. "13.5.4.3"
        val isShizukuInstalled: Boolean = false,
    )

    suspend fun detect(): Result = withContext(Dispatchers.IO) {
        val installed = isShizukuInstalled()

        val running = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            XLog.tag(TAG).d("pingBinder failed", e)
            false
        }

        if (!running) {
            XLog.tag(TAG).d("detect: not running, installed=$installed")
            return@withContext Result(isShizukuInstalled = installed)
        }

        val authorized = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            XLog.tag(TAG).d("checkSelfPermission failed", e)
            false
        }

        // UID 0 = Root-based (Sui or Shizuku root-start); UID 2000 = ADB-based
        val uid = try { Shizuku.getUid() } catch (_: Exception) { -1 }
        val authMethod = when (uid) {
            0    -> AuthMethod.ROOT
            2000 -> AuthMethod.ADB
            else -> null
        }

        // Prefer full version string from package (e.g. "13.5.4.3") over API version int (e.g. 13)
        val version: String? = try {
            context.packageManager
                .getPackageInfo("moe.shizuku.privileged.api", 0)
                .versionName
        } catch (_: PackageManager.NameNotFoundException) {
            // Package not installed — Sui or other Shizuku-compat provider; fall back to API version
            try { Shizuku.getVersion().toString() } catch (_: Exception) { null }
        }

        XLog.tag(TAG).d("detect: running=true authorized=$authorized uid=$uid version=$version installed=$installed")

        Result(
            isRunning = true,
            isAuthorized = authorized,
            authMethod = authMethod,
            version = version,
            isShizukuInstalled = installed,
        )
    }

    private fun isShizukuInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
