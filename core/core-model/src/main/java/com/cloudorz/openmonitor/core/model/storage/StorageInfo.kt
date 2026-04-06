package com.cloudorz.openmonitor.core.model.storage

data class StorageInfo(
    val internalStorage: StorageVolume = StorageVolume(),
    val externalStorage: StorageVolume? = null,
    val partitions: List<PartitionInfo> = emptyList(),
    val storageType: String = "",
    val fileSystem: String = "",
    val blockSizeBytes: Long = 0,
)

data class StorageVolume(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
) {
    val usedBytes: Long
        get() = totalBytes - availableBytes

    val usedPercent: Double
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes) * 100.0 else 0.0
}

data class PartitionInfo(
    val name: String,
    val path: String,
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
) {
    val usedBytes: Long
        get() = totalBytes - availableBytes

    val usedPercent: Double
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes) * 100.0 else 0.0
}

/** Detailed mount info from /proc/mounts — used in partition detail screen. */
data class MountInfo(
    val device: String = "",
    val mountPoint: String = "",
    val fileSystem: String = "",
    val options: String = "",
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
) {
    val usedBytes: Long
        get() = totalBytes - availableBytes

    val usedPercent: Double
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes) * 100.0 else 0.0

    val isReadOnly: Boolean
        get() = options.split(",").any { it.trim() == "ro" }
}
