package com.cloudorz.monitor.core.model.cpu

data class CpuCacheInfo(
    val l1dCacheSizes: List<Int> = emptyList(),
    val l1iCacheSizes: List<Int> = emptyList(),
    val l2CacheSizes: List<Int> = emptyList(),
    val l3CacheSizes: List<Int> = emptyList(),
) {
    val l1dSummary: String
        get() = formatCacheSummary(l1dCacheSizes)

    val l1iSummary: String
        get() = formatCacheSummary(l1iCacheSizes)

    val l2Summary: String
        get() = formatCacheSummary(l2CacheSizes)

    val l3Summary: String
        get() = formatCacheSummary(l3CacheSizes)

    val hasData: Boolean
        get() = l1dCacheSizes.isNotEmpty() || l2CacheSizes.isNotEmpty() || l3CacheSizes.isNotEmpty()

    private fun formatCacheSummary(sizes: List<Int>): String {
        if (sizes.isEmpty()) return ""
        val distinct = sizes.distinct().sorted()
        return distinct.joinToString(" + ") { formatBytes(it) } +
            if (sizes.size > distinct.size) " x${sizes.size / distinct.size}" else ""
    }

    companion object {
        fun formatBytes(bytes: Int): String = when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
