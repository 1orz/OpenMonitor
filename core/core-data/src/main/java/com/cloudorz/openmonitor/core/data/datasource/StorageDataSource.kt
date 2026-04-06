package com.cloudorz.openmonitor.core.data.datasource

import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.cloudorz.openmonitor.core.model.storage.MountInfo
import com.cloudorz.openmonitor.core.model.storage.PartitionInfo
import com.cloudorz.openmonitor.core.model.storage.StorageInfo
import com.cloudorz.openmonitor.core.model.storage.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageDataSource @Inject constructor() {

    companion object {
        private const val TAG = "StorageDataSource"
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val internal = getVolumeInfo(Environment.getDataDirectory())
        val externalDirs = getExternalStoragePaths()
        val external = externalDirs.firstOrNull()?.let { getVolumeInfo(it) }
        val partitions = getPartitions()

        StorageInfo(
            internalStorage = internal,
            externalStorage = external,
            partitions = partitions,
            storageType = detectStorageType(),
            fileSystem = detectFileSystem(),
            blockSizeBytes = detectBlockSize(),
        )
    }

    private fun detectStorageType(): String {
        return try {
            when {
                File("/sys/class/block/sda").exists() -> "UFS"
                File("/sys/block/mmcblk0").exists() -> "eMMC"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun detectFileSystem(): String {
        return try {
            File("/proc/mounts").readLines()
                .firstOrNull { line ->
                    val parts = line.split("\\s+".toRegex())
                    parts.size >= 3 && parts[1] == "/data"
                }
                ?.split("\\s+".toRegex())
                ?.getOrNull(2) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun detectBlockSize(): Long {
        return try {
            StatFs(Environment.getDataDirectory().absolutePath).blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    private fun getVolumeInfo(path: File): StorageVolume {
        return try {
            val stat = StatFs(path.absolutePath)
            StorageVolume(
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes,
            )
        } catch (e: Exception) {
            Log.d(TAG, "getVolumeInfo failed: ${path.absolutePath}", e)
            StorageVolume()
        }
    }

    private fun getExternalStoragePaths(): List<File> {
        val externalStorage = System.getenv("SECONDARY_STORAGE")
        if (!externalStorage.isNullOrBlank()) {
            return externalStorage.split(":").mapNotNull { path ->
                File(path).takeIf { it.exists() && it.canRead() }
            }
        }
        return emptyList()
    }

    /**
     * Parse /proc/mounts to get all mounted partitions with detailed info.
     * Filters out virtual/pseudo filesystems to show only real partitions.
     */
    suspend fun getAllMounts(): List<MountInfo> = withContext(Dispatchers.IO) {
        val ignoredFs = setOf(
            "tmpfs", "devpts", "proc", "sysfs", "selinuxfs", "debugfs",
            "tracefs", "bpf", "cgroup", "cgroup2", "pstore", "configfs",
            "functionfs", "adb", "mtp", "ptp", "binder", "devtmpfs",
            "securityfs", "fusectl", "none",
        )
        try {
            File("/proc/mounts").readLines().mapNotNull { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 4) return@mapNotNull null
                val device = parts[0]
                val mountPoint = decodeMountPath(parts[1])
                val fs = parts[2]
                val options = parts[3]

                // Filter out pseudo filesystems
                if (fs in ignoredFs) return@mapNotNull null
                if (device == "none" || device == "tmpfs") return@mapNotNull null

                // Get size info via StatFs — guard against inaccessible paths
                val (total, available) = try {
                    val f = File(mountPoint)
                    if (!f.exists() || !f.canRead()) throw SecurityException("cannot access $mountPoint")
                    val stat = StatFs(mountPoint)
                    stat.totalBytes to stat.availableBytes
                } catch (e: Exception) {
                    0L to 0L
                }

                MountInfo(
                    device = device,
                    mountPoint = mountPoint,
                    fileSystem = fs,
                    options = options,
                    totalBytes = total,
                    availableBytes = available,
                )
            }.sortedWith(compareByDescending<MountInfo> { it.totalBytes }.thenBy { it.mountPoint })
        } catch (e: Exception) {
            Log.d(TAG, "getAllMounts failed", e)
            emptyList()
        }
    }

    private fun getPartitions(): List<PartitionInfo> {
        val partitionPaths = listOf(
            "System" to "/system",
            "Cache" to "/cache",
            "Data" to "/data",
        )

        return partitionPaths.mapNotNull { (name, path) ->
            try {
                val file = File(path)
                if (!file.exists()) return@mapNotNull null
                val stat = StatFs(path)
                PartitionInfo(
                    name = name,
                    path = path,
                    totalBytes = stat.totalBytes,
                    availableBytes = stat.availableBytes,
                )
            } catch (e: Exception) {
                Log.d(TAG, "getPartitions failed: $path", e)
                null
            }
        }
    }

    /** Decode octal escapes in /proc/mounts paths (e.g. \040 → space). */
    private fun decodeMountPath(encoded: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < encoded.length) {
            if (encoded[i] == '\\' && i + 3 < encoded.length) {
                val oct = encoded.substring(i + 1, i + 4)
                val code = oct.toIntOrNull(8)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 4
                    continue
                }
            }
            sb.append(encoded[i])
            i++
        }
        return sb.toString()
    }
}
