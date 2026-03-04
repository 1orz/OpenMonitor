package com.cloudorz.monitor.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the current privilege mode and provides the appropriate [ShellExecutor]
 * for the detected or user-selected mode.
 *
 * On first use, call [detectBestMode] to automatically probe for the highest available
 * privilege level. The detection order is: ROOT > SHIZUKU > ADB > BASIC.
 *
 * The current mode is exposed as a [StateFlow] so that UI layers can reactively
 * update when the privilege mode changes.
 */
@Singleton
class PermissionManager @Inject constructor(
    private val rootExecutor: RootExecutor,
    private val shizukuExecutor: ShizukuExecutor,
    private val adbExecutor: AdbExecutor,
    private val basicExecutor: BasicExecutor,
) {
    private val _currentMode = MutableStateFlow(PrivilegeMode.BASIC)

    /** Observable state of the current [PrivilegeMode]. */
    val currentMode: StateFlow<PrivilegeMode> = _currentMode.asStateFlow()

    /**
     * Probes the system for the highest available privilege level and updates [currentMode].
     *
     * Detection order:
     * 1. **ROOT** -- checks if the app has been granted root via libsu.
     * 2. **SHIZUKU** -- checks if the Shizuku binder is alive and permission is granted.
     * 3. **ADB** -- checks if the shell has elevated permissions by attempting to read
     *    a protected system file (`/data/system/packages.xml`).
     * 4. **BASIC** -- always available as the final fallback.
     *
     * @return The detected [PrivilegeMode].
     */
    suspend fun detectBestMode(): PrivilegeMode = withContext(Dispatchers.IO) {
        val detected = when {
            rootExecutor.isAvailable() -> PrivilegeMode.ROOT
            shizukuExecutor.isAvailable() -> PrivilegeMode.SHIZUKU
            hasElevatedShellAccess() -> PrivilegeMode.ADB
            else -> PrivilegeMode.BASIC
        }
        _currentMode.value = detected
        detected
    }

    /**
     * Manually overrides the current privilege mode.
     *
     * @param mode The [PrivilegeMode] to set.
     */
    fun setMode(mode: PrivilegeMode) {
        _currentMode.value = mode
    }

    /**
     * Returns the [ShellExecutor] corresponding to the current [PrivilegeMode].
     */
    fun getExecutor(): ShellExecutor {
        return when (_currentMode.value) {
            PrivilegeMode.ROOT -> rootExecutor
            PrivilegeMode.ADB -> adbExecutor
            PrivilegeMode.SHIZUKU -> shizukuExecutor
            PrivilegeMode.BASIC -> basicExecutor
        }
    }

    /**
     * Returns the [ShellExecutor] for a specific [PrivilegeMode], regardless
     * of the currently active mode.
     */
    fun getExecutor(mode: PrivilegeMode): ShellExecutor {
        return when (mode) {
            PrivilegeMode.ROOT -> rootExecutor
            PrivilegeMode.ADB -> adbExecutor
            PrivilegeMode.SHIZUKU -> shizukuExecutor
            PrivilegeMode.BASIC -> basicExecutor
        }
    }

    /**
     * Checks whether the shell has elevated (ADB-level) permissions by attempting
     * to read a file that is normally only accessible to the system or shell user.
     */
    private suspend fun hasElevatedShellAccess(): Boolean {
        return try {
            val result = adbExecutor.execute("cat /data/system/packages.xml | head -c 1")
            result.isSuccess && result.stdout.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
