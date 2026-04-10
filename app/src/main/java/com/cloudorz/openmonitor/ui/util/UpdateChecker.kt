package com.cloudorz.openmonitor.ui.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.cloudorz.openmonitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val downloadUrl: String = "",
    val changelog: String = "",
    val hasUpdate: Boolean = false,
    val isError: Boolean = false,
)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/1orz/OpenMonitor/releases/latest"
    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_CACHED_JSON = "cached_json"
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24h

    // APK naming: OpenMonitor-v1.2.3_10203-arm64-v8a-release.apk
    private val APK_REGEX = Regex("""OpenMonitor-v(.+?)_(\d+)-(.+?)-release\.apk""")

    suspend fun check(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)

        // Use cache if fresh enough
        val json = if (now - lastCheck < CACHE_DURATION_MS) {
            prefs.getString(KEY_CACHED_JSON, null)?.let { JSONObject(it) }
        } else null

        val releaseJson = json ?: fetchRelease() ?: return@withContext UpdateInfo(isError = true)

        // Cache the result
        if (json == null) {
            prefs.edit {
                putLong(KEY_LAST_CHECK, now)
                putString(KEY_CACHED_JSON, releaseJson.toString())
            }
        }

        parseRelease(releaseJson)
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun fetchRelease(): JSONObject? {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRelease(json: JSONObject): UpdateInfo {
        val changelog = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return UpdateInfo()
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        // First pass: find matching ABI
        // Second pass: fallback to universal
        var bestMatch: UpdateInfo? = null
        var universalMatch: UpdateInfo? = null

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (!name.endsWith(".apk")) continue

            val match = APK_REGEX.find(name) ?: continue
            val versionName = match.groupValues[1]
            val versionCode = match.groupValues[2].toIntOrNull() ?: continue
            val abi = match.groupValues[3]
            val downloadUrl = asset.getString("browser_download_url")

            val info = UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                changelog = changelog,
                hasUpdate = versionCode > BuildConfig.VERSION_CODE,
            )

            when {
                abi == deviceAbi -> bestMatch = info
                abi == "universal" -> universalMatch = info
            }
        }

        return bestMatch ?: universalMatch ?: UpdateInfo()
    }

    fun openDownload(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {}
    }
}
