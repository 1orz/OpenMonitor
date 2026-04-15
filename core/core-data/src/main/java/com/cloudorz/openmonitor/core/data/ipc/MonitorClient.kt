package com.cloudorz.openmonitor.core.data.ipc

import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.cloudorz.openmonitor.server.IMonitorCallback
import com.cloudorz.openmonitor.server.IMonitorService
import com.cloudorz.openmonitor.server.ServerSnapshot
import com.cloudorz.openmonitor.server.SnapshotCodec
import com.cloudorz.openmonitor.server.SnapshotLayout
import com.elvishew.xlog.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Obtains an [IMonitorService] binder (delivered by Shizuku UserService
 * onServiceConnected, or by [BinderProvider.setBinder] in the libsu path),
 * maps the shared [ParcelFileDescriptor] backing the Snapshot region into
 * a read-only [ByteBuffer], and emits decoded [ServerSnapshot]s on a Flow.
 *
 * Resilience: [IBinder.linkToDeath] triggers [onServerDied] which clears
 * state and notifies [MonitorLauncher] to re-launch.
 */
@Singleton
class MonitorClient @Inject constructor() {

    companion object {
        private const val TAG = "MonitorClient"
        /** Read every ~100 ms; Choreographer integration can come later. */
        private const val READ_INTERVAL_MS: Long = 100
    }

    // --- state ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var readerJob: Job? = null

    private var service: IMonitorService? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var pfd: ParcelFileDescriptor? = null
    private var mappedBuffer: ByteBuffer? = null

    // --- public Flow surface ---
    private val _snapshots = MutableSharedFlow<ServerSnapshot>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val snapshots: SharedFlow<ServerSnapshot> = _snapshots.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    // --- callback pump (focused pkg, screen) ---
    private val _focusedPackage = MutableStateFlow<String?>(null)
    val focusedPackage: StateFlow<String?> = _focusedPackage.asStateFlow()

    private val _screenInteractive = MutableStateFlow(true)
    val screenInteractive: StateFlow<Boolean> = _screenInteractive.asStateFlow()

    private val callback = object : IMonitorCallback.Stub() {
        override fun onFocusedPackageChanged(pkg: String?) {
            _focusedPackage.value = pkg
        }
        override fun onScreenStateChanged(interactive: Boolean) {
            _screenInteractive.value = interactive
        }
        override fun onError(code: Int, msg: String?) {
            XLog.tag(TAG).e("server error $code: $msg")
        }
    }

    /**
     * Entry point called by the launcher layer once a binder is available.
     * Safe to call repeatedly; replaces an existing binder atomically.
     */
    @Synchronized
    fun onBinderReceived(binder: IBinder) {
        disposeLocked()

        val svc = IMonitorService.Stub.asInterface(binder) ?: run {
            XLog.tag(TAG).e("asInterface returned null — wire protocol mismatch?")
            return
        }

        val version = runCatching { svc.version }.getOrNull() ?: -1
        XLog.tag(TAG).i("server version=$version")

        val recipient = IBinder.DeathRecipient { onServerDied() }
        runCatching { binder.linkToDeath(recipient, 0) }
            .onFailure { XLog.tag(TAG).e("linkToDeath failed: ${it.message}") }

        val snapshotPfd = runCatching { svc.snapshotMemory }.getOrNull()
        if (snapshotPfd == null) {
            XLog.tag(TAG).e("getSnapshotMemory returned null")
            return
        }

        val buffer = mapSnapshotFd(snapshotPfd)
        if (buffer == null) {
            XLog.tag(TAG).e("mmap snapshot FD failed")
            runCatching { snapshotPfd.close() }
            return
        }

        service = svc
        pfd = snapshotPfd
        mappedBuffer = buffer
        deathRecipient = recipient

        runCatching { svc.registerCallback(callback) }
            .onFailure { XLog.tag(TAG).e("registerCallback failed: ${it.message}") }

        _connected.value = true
        startReader(buffer)
    }

    fun setSamplingRate(subsystem: String, intervalMs: Int) {
        runCatching { service?.setSamplingRate(subsystem, intervalMs) }
    }

    fun setActiveSubsystems(names: List<String>) {
        runCatching { service?.setActiveSubsystems(names.toTypedArray()) }
    }

    fun shutdown() {
        scope.cancel()
        disposeLocked()
    }

    // --- internals ---

    private fun startReader(buffer: ByteBuffer) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.Default) {
            var lastSeq = -1L
            while (isActive) {
                val snap = SnapshotCodec.read(buffer)
                if (snap != null && snap.seq != lastSeq) {
                    lastSeq = snap.seq
                    _snapshots.tryEmit(snap)
                }
                delay(READ_INTERVAL_MS)
            }
        }
    }

    private fun onServerDied() {
        XLog.tag(TAG).e("server binder died — clearing state")
        synchronized(this) {
            _connected.value = false
            disposeLocked()
        }
        // MonitorLauncher observes `connected == false` and relaunches.
    }

    private fun disposeLocked() {
        readerJob?.cancel()
        readerJob = null

        service?.let { svc ->
            runCatching { svc.unregisterCallback(callback) }
            deathRecipient?.let { r ->
                runCatching { svc.asBinder().unlinkToDeath(r, 0) }
            }
        }
        service = null
        deathRecipient = null

        mappedBuffer = null
        pfd?.let { runCatching { it.close() } }
        pfd = null
    }

    private fun mapSnapshotFd(pfd: ParcelFileDescriptor): ByteBuffer? {
        return try {
            // ashmem regions are backed by an unnamed file descriptor; we
            // mmap as read-only with MAP_SHARED so the server's seqlock
            // writes become visible to us.
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel: FileChannel = fis.channel
                val buf = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0L,
                    SnapshotLayout.SIZE_BYTES.toLong()
                )
                buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            }
        } catch (e: Throwable) {
            XLog.tag(TAG).e("mapSnapshotFd: ${e.message}")
            null
        }
    }
}
