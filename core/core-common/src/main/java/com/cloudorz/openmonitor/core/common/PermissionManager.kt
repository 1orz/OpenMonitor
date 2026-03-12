package com.cloudorz.openmonitor.core.common

import android.content.Context
import com.elvishew.xlog.XLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val rootExecutor: RootExecutor,
    private val shizukuExecutor: ShizukuExecutor,
    private val adbExecutor: AdbExecutor,
    private val basicExecutor: BasicExecutor,
) {
    companion object {
        private const val TAG = "PermissionManager"
    }

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _currentMode = MutableStateFlow(PrivilegeMode.BASIC)

    val currentMode: StateFlow<PrivilegeMode> = _currentMode.asStateFlow()

    val hasPersistedMode: Boolean

    /** Mirrors ShizukuExecutor.binderAlive — true while Shizuku is attached. */
    val shizukuBinderAlive: StateFlow<Boolean> get() = shizukuExecutor.binderAlive

    /** Result of the most recent Shizuku permission request. Null until user responds. */
    val shizukuPermissionResult: StateFlow<Boolean?> get() = shizukuExecutor.permissionResult

    fun resetShizukuPermissionResult() = shizukuExecutor.resetPermissionResult()

    /** True if Shizuku binder is alive AND permission is granted (synchronous). */
    fun isShizukuAvailableSync(): Boolean = shizukuExecutor.isAvailableSync()

    /** Triggers the Shizuku permission grant dialog if not yet granted. */
    fun requestShizukuPermission() = shizukuExecutor.requestPermissionIfNeeded()

    init {
        val saved = prefs.getString("privilege_mode", null)
        hasPersistedMode = saved != null
        if (saved != null) {
            val mode = try {
                PrivilegeMode.valueOf(saved)
            } catch (_: IllegalArgumentException) {
                PrivilegeMode.BASIC
            }
            _currentMode.value = mode
            if (mode == PrivilegeMode.SHIZUKU) {
                shizukuExecutor.bindService()
            }
        }
    }

    suspend fun detectBestMode(): PrivilegeMode = withContext(Dispatchers.IO) {
        val rootAvailable = withTimeoutOrNull(5000L) {
            rootExecutor.isAvailable()
        } ?: false
        val detected = when {
            rootAvailable -> PrivilegeMode.ROOT
            shizukuExecutor.isAvailable() -> PrivilegeMode.SHIZUKU
            hasElevatedShellAccess() -> PrivilegeMode.ADB
            else -> PrivilegeMode.BASIC
        }
        _currentMode.value = detected
        persistMode(detected)
        if (detected == PrivilegeMode.SHIZUKU) {
            shizukuExecutor.bindService()
        }
        detected
    }

    fun setMode(mode: PrivilegeMode) {
        val oldMode = _currentMode.value
        _currentMode.value = mode
        persistMode(mode)

        // Manage Shizuku UserService lifecycle
        if (mode == PrivilegeMode.SHIZUKU && oldMode != PrivilegeMode.SHIZUKU) {
            shizukuExecutor.bindService()
        } else if (mode != PrivilegeMode.SHIZUKU && oldMode == PrivilegeMode.SHIZUKU) {
            shizukuExecutor.unbindService()
        }
    }

    fun getExecutor(): ShellExecutor {
        return when (_currentMode.value) {
            PrivilegeMode.ROOT -> rootExecutor
            PrivilegeMode.ADB -> adbExecutor
            PrivilegeMode.SHIZUKU -> shizukuExecutor
            PrivilegeMode.BASIC -> basicExecutor
        }
    }

    fun getExecutor(mode: PrivilegeMode): ShellExecutor {
        return when (mode) {
            PrivilegeMode.ROOT -> rootExecutor
            PrivilegeMode.ADB -> adbExecutor
            PrivilegeMode.SHIZUKU -> shizukuExecutor
            PrivilegeMode.BASIC -> basicExecutor
        }
    }

    private fun persistMode(mode: PrivilegeMode) {
        prefs.edit { putString("privilege_mode", mode.name) }
    }

    private suspend fun hasElevatedShellAccess(): Boolean {
        return try {
            val result = adbExecutor.execute("cat /data/system/packages.xml | head -c 1")
            result.isSuccess && result.stdout.isNotEmpty()
        } catch (e: Exception) {
            XLog.tag(TAG).d("hasElevatedShellAccess check failed", e)
            false
        }
    }
}
