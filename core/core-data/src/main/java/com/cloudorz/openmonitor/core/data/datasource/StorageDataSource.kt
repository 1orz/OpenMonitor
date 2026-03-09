package com.cloudorz.openmonitor.core.data.datasource

import android.os.Environment
import android.os.StatFs
import android.util.Log
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
        )
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
}
