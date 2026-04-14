package com.cloudorz.openmonitor.feature.keyattestation.keystore

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Manages the lifecycle of the [KeyAttestKeyStoreService] Shizuku user service.
 *
 * Call [bind] when the user enables Shizuku mode; [unbind] when they disable it.
 * The [remoteKeyStore] flow emits the connected service, or null when disconnected.
 */
object ShizukuKeyStoreManager {
    private const val TAG = "ShizukuKSManager"

    private val _remoteKeyStore = MutableStateFlow<IKeyAttestKeyStore?>(null)
    val remoteKeyStore: StateFlow<IKeyAttestKeyStore?> = _remoteKeyStore

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.cloudorz.openmonitor",
            KeyAttestKeyStoreService::class.java.name,
        )
    )
        .daemon(false)
        .processNameSuffix("keyattestation_ks")
        .debuggable(false)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "service connected")
            _remoteKeyStore.value = IKeyAttestKeyStore.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "service disconnected")
            _remoteKeyStore.value = null
        }
    }

    fun bind() {
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            Log.w(TAG, "bindUserService failed", e)
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (e: Exception) {
            Log.w(TAG, "unbindUserService failed", e)
        }
        _remoteKeyStore.value = null
    }
}
