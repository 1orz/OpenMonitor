package com.cloudorz.openmonitor.core.common

import android.content.Context
import android.content.pm.PackageManager
import com.elvishew.xlog.XLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the installed Root framework (Magisk / KernelSU / APatch) and su binary presence
 * WITHOUT calling `su` — so no authorization dialog is triggered for new users.
 *
 * Detection strategy:
 * - Check installed packages (works without any privileges)
 * - Read /proc/sys/kernel/ksu_version (world-readable, kernel-exported by KernelSU)
 * - Check well-known su binary paths via File.exists()
 */
@Singleton
class RootFrameworkDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RootFrameworkDetector"
    }

    enum class Framework { MAGISK, KERNELSU, APATCH }

    data class Result(
        val framework: Framework? = null,
        val version: String? = null,
        val packageName: String? = null,
        val hasSuBinary: Boolean = false,
    ) {
        val isAvailable: Boolean get() = framework != null || hasSuBinary
    }

    suspend fun detect(): Result = withContext(Dispatchers.IO) {
        val magisk = detectMagisk()
        val ksu = detectKernelSU()
        val apatch = detectAPatch()
        val hasSu = checkSuBinary()

        val framework: Framework?
        val version: String?
        val pkg: String?
        when {
            magisk != null  -> { framework = Framework.MAGISK;    version = magisk.first;  pkg = magisk.second }
            ksu != null     -> { framework = Framework.KERNELSU;  version = ksu.first;     pkg = ksu.second }
            apatch != null  -> { framework = Framework.APATCH;    version = apatch.first;  pkg = apatch.second }
            else            -> { framework = null;                version = null;          pkg = null }
        }

        XLog.tag(TAG).d("detect: framework=$framework version=$version hasSu=$hasSu")
        Result(framework, version, pkg, hasSu)
    }

    // Returns Pair(version, packageName) or null if not found.
    // version may be null if the framework is detected but no manager package is installed.
    private fun detectMagisk(): Pair<String?, String>? {
        val candidates = listOf(
            "com.topjohnwu.magisk",         // Official Magisk
            "io.github.huskydg.magisk",      // Magisk Delta (fork)
        )
        for (pkg in candidates) {
            try {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val ver = info.versionName ?: info.longVersionCode.toString()
                XLog.tag(TAG).d("detectMagisk: found $pkg v$ver")
                return ver to pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    private fun detectKernelSU(): Pair<String?, String>? {
        // PackageManager gives the manager app version, NOT ksud binary version.
        // ksud --version requires root — so we only detect presence here, not version.
        val candidates = listOf(
            "me.weishu.kernelsu",            // Official KernelSU
            "com.rifsxd.ksunext",            // KernelSU Next (community fork)
        )
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                XLog.tag(TAG).d("detectKernelSU: found $pkg (version requires root)")
                return null to pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    private fun detectAPatch(): Pair<String?, String>? {
        // Same reasoning as KernelSU — apd version requires root.
        val candidates = listOf(
            "me.bmax.apatch",                // APatch manager
        )
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                XLog.tag(TAG).d("detectAPatch: found $pkg (version requires root)")
                return null to pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    private fun checkSuBinary(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/tmp/su",
        )
        return paths.any { path ->
            File(path).exists().also { exists ->
                if (exists) XLog.tag(TAG).d("checkSuBinary: found $path")
            }
        }
    }
}
