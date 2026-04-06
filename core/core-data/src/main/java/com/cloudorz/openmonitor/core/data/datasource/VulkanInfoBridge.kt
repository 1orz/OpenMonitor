package com.cloudorz.openmonitor.core.data.datasource

import android.util.Log

/**
 * JNI bridge to query Vulkan device information via native Vulkan API.
 * Returns a JSON string with full device properties, features, memory, queues, extensions.
 */
object VulkanInfoBridge {

    private const val TAG = "VulkanInfoBridge"

    private val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("vulkan-info")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "vulkan-info library not available", e)
            false
        }
    }

    fun getVulkanInfoJson(): String {
        if (!isAvailable) return "{}"
        return try {
            nGetVulkanInfoJson()
        } catch (e: Throwable) {
            Log.d(TAG, "nGetVulkanInfoJson failed", e)
            "{}"
        }
    }

    private external fun nGetVulkanInfoJson(): String
}
