package com.cloudorz.monitor.core.common

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuNativeInfo @Inject constructor() {

    val isAvailable: Boolean
        get() = libraryLoaded

    fun getCpuName(): String? {
        if (!libraryLoaded) return null
        return try {
            val name = nativeGetCpuName()
            name.ifEmpty { null }
        } catch (e: Exception) {
            Log.d(TAG, "getCpuName failed", e)
            null
        }
    }

    fun getCoreCount(): Int? {
        if (!libraryLoaded) return null
        return try {
            val count = nativeGetCoreCount()
            if (count > 0) count else null
        } catch (e: Exception) {
            Log.d(TAG, "getCoreCount failed", e)
            null
        }
    }

    fun hasArmNeon(): Boolean? {
        if (!libraryLoaded) return null
        return try {
            nativeHasArmNeon()
        } catch (e: Exception) {
            Log.d(TAG, "hasArmNeon failed", e)
            null
        }
    }

    fun getL1dCacheSizes(): IntArray? {
        if (!libraryLoaded) return null
        return try {
            nativeGetL1dCaches()
        } catch (e: Exception) {
            Log.d(TAG, "getL1dCacheSizes failed", e)
            null
        }
    }

    fun getL1iCacheSizes(): IntArray? {
        if (!libraryLoaded) return null
        return try {
            nativeGetL1iCaches()
        } catch (e: Exception) {
            Log.d(TAG, "getL1iCacheSizes failed", e)
            null
        }
    }

    fun getL2CacheSizes(): IntArray? {
        if (!libraryLoaded) return null
        return try {
            nativeGetL2Caches()
        } catch (e: Exception) {
            Log.d(TAG, "getL2CacheSizes failed", e)
            null
        }
    }

    fun getL3CacheSizes(): IntArray? {
        if (!libraryLoaded) return null
        return try {
            nativeGetL3Caches()
        } catch (e: Exception) {
            Log.d(TAG, "getL3CacheSizes failed", e)
            null
        }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeGetCpuName(): String
    private external fun nativeGetCoreCount(): Int
    private external fun nativeHasArmNeon(): Boolean
    private external fun nativeGetL1dCaches(): IntArray?
    private external fun nativeGetL1iCaches(): IntArray?
    private external fun nativeGetL2Caches(): IntArray?
    private external fun nativeGetL3Caches(): IntArray?

    companion object {
        private const val TAG = "CpuNativeInfo"
        private var libraryLoaded = false

        init {
            try {
                System.loadLibrary("cpuinfo-bridge")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load cpuinfo-bridge native library", e)
                libraryLoaded = false
            }
        }
    }
}
