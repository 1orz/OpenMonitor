package com.cloudorz.openmonitor.core.data.ipc

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.cloudorz.openmonitor.server.ServerSnapshot
import com.elvishew.xlog.XLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AF_UNIX client for the Rust openmonitor-server daemon.
 *
 * Wire protocol: `[u32 big-endian payload_len] [payload_len bytes UTF-8 JSON]`.
 * After connect, the server writes an `auth_ok` or `auth_fail` frame; then the
 * client sends `subscribe` and reads snapshot frames in a loop.
 */
@Singleton
class DaemonClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "DaemonClient"
        const val PROTOCOL_VERSION = 3
        private const val MAX_FRAME_BYTES = 1 * 1024 * 1024
        private const val SUBSCRIBE_INTERVAL_MS = 500
        private const val CONNECT_TIMEOUT_MS = 3000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null
    private var socket: LocalSocket? = null
    private var dataIn: DataInputStream? = null
    private var dataOut: DataOutputStream? = null

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    private val _snapshots = MutableSharedFlow<ServerSnapshot>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val snapshots: SharedFlow<ServerSnapshot> = _snapshots.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * Try all candidate paths in order. Returns true if one connected + auth'd.
     *
     * If no explicit [candidatePaths] are given, the default discovery order is:
     * 1. `$filesDir/openmonitor.sock`
     * 2. content of `$filesDir/sock.path`
     * 3. content of `/data/local/tmp/openmonitor/sock.path`
     * 4. `/data/local/tmp/openmonitor/openmonitor.sock`
     */
    suspend fun connect(candidatePaths: List<String> = defaultCandidatePaths()): Boolean {
        disconnect()

        for (path in candidatePaths) {
            XLog.tag(TAG).i("trying socket path: $path")
            try {
                val sock = LocalSocket()
                sock.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                // LocalSocket doesn't expose a connect timeout directly;
                // the underlying unix connect() on SOCK_STREAM returns
                // immediately for filesystem sockets (no TCP handshake).

                val din = DataInputStream(sock.inputStream)
                val dout = DataOutputStream(sock.outputStream)

                // Server writes first: auth_ok or auth_fail
                val authPayload = readFrame(din)
                if (authPayload == null) {
                    XLog.tag(TAG).w("$path: server closed before auth frame")
                    runCatching { sock.close() }
                    continue
                }

                val element = try {
                    json.parseToJsonElement(authPayload)
                } catch (e: Exception) {
                    XLog.tag(TAG).w("$path: failed to parse auth frame: ${e.message}")
                    runCatching { sock.close() }
                    continue
                }

                val type = element.jsonObject["type"]?.jsonPrimitive?.content
                when (type) {
                    "auth_ok" -> {
                        val frame = json.decodeFromJsonElement(ServerFrame.AuthOk.serializer(), element)
                        if (frame.version != PROTOCOL_VERSION) {
                            XLog.tag(TAG).w(
                                "$path: version mismatch server=${frame.version} " +
                                    "client=$PROTOCOL_VERSION"
                            )
                        }
                        XLog.tag(TAG).i("connected to $path (server v${frame.version})")
                    }

                    "auth_fail" -> {
                        val frame = json.decodeFromJsonElement(ServerFrame.AuthFail.serializer(), element)
                        XLog.tag(TAG).w("$path: auth_fail reason=${frame.reason}")
                        runCatching { sock.close() }
                        continue
                    }

                    else -> {
                        XLog.tag(TAG).w("$path: unexpected first frame type: $type")
                        runCatching { sock.close() }
                        continue
                    }
                }

                // Commit connection state
                socket = sock
                dataIn = din
                dataOut = dout
                _connected.value = true

                // Subscribe and start reader
                sendCmd("""{"cmd":"subscribe","interval_ms":$SUBSCRIBE_INTERVAL_MS}""")
                startReader()
                return true
            } catch (e: IOException) {
                XLog.tag(TAG).d("$path: connect failed: ${e.message}")
            } catch (e: Exception) {
                XLog.tag(TAG).w("$path: unexpected error: ${e.message}")
            }
        }

        XLog.tag(TAG).w("all candidate paths exhausted, connect failed")
        return false
    }

    /** Send `{"cmd":"exit"}` to ask the daemon to shut itself down. Best-effort. */
    fun requestExit() {
        try {
            sendCmd("""{"cmd":"exit"}""")
        } catch (e: Exception) {
            XLog.tag(TAG).d("requestExit failed (best-effort): ${e.message}")
        }
    }

    /** Drop the current connection without exit'ing the server. Used on mode switch. */
    @Synchronized
    fun disconnect() {
        _connected.value = false
        readerJob?.cancel()
        readerJob = null
        dataOut = null
        dataIn = null
        runCatching { socket?.close() }
        socket = null
    }

    // -- internals ----------------------------------------------------------------

    private fun defaultCandidatePaths(): List<String> {
        val filesDir = context.filesDir.absolutePath
        val fallbackDir = "/data/local/tmp/openmonitor"
        return buildList {
            add("$filesDir/openmonitor.sock")
            readSockPathFile("$filesDir/sock.path")?.let { add(it) }
            readSockPathFile("$fallbackDir/sock.path")?.let { add(it) }
            add("$fallbackDir/openmonitor.sock")
        }
    }

    private fun readSockPathFile(path: String): String? {
        return try {
            val f = File(path)
            if (f.exists()) f.readText().trim().takeIf { it.isNotEmpty() } else null
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun sendCmd(jsonStr: String) {
        val out = dataOut ?: return
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun readFrame(din: DataInputStream): String? {
        val len = try {
            din.readInt()
        } catch (_: java.io.EOFException) {
            return null
        }
        if (len < 0 || len > MAX_FRAME_BYTES) {
            throw IOException("frame size out of range: $len")
        }
        val buf = ByteArray(len)
        din.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val din = dataIn ?: return@launch
            try {
                while (isActive) {
                    val payload = readFrame(din) ?: run {
                        XLog.tag(TAG).i("server closed connection (EOF)")
                        break
                    }

                    val element = try {
                        json.parseToJsonElement(payload)
                    } catch (e: Exception) {
                        XLog.tag(TAG).w("failed to parse frame JSON: ${e.message}")
                        continue
                    }

                    val type = element.jsonObject["type"]?.jsonPrimitive?.content
                    when (type) {
                        "snapshot" -> {
                            val snap = json.decodeFromJsonElement(
                                ServerSnapshot.serializer(), element
                            )
                            _snapshots.tryEmit(snap)
                        }

                        "pong" -> {
                            XLog.tag(TAG).d("pong")
                        }

                        "error" -> {
                            val frame = json.decodeFromJsonElement(
                                ServerFrame.Error.serializer(), element
                            )
                            XLog.tag(TAG).w("server error ${frame.code}: ${frame.msg}")
                        }

                        "auth_ok" -> {
                            XLog.tag(TAG).d("unexpected auth_ok in reader loop")
                        }

                        "auth_fail" -> {
                            val frame = json.decodeFromJsonElement(
                                ServerFrame.AuthFail.serializer(), element
                            )
                            XLog.tag(TAG).w("unexpected auth_fail in reader loop: ${frame.reason}")
                        }

                        else -> {
                            XLog.tag(TAG).w("unknown frame type: $type")
                        }
                    }
                }
            } catch (_: IOException) {
                XLog.tag(TAG).i("reader: socket closed / IO error")
            } catch (e: Exception) {
                XLog.tag(TAG).e("reader: unexpected error: ${e.message}")
            } finally {
                _connected.value = false
            }
        }
    }
}

// =============================================================================
// Wire-protocol types for non-snapshot frames (kotlinx.serialization)
// Snapshot frames are decoded directly to ServerSnapshot from SnapshotLayout.kt.
// =============================================================================

@Serializable
sealed class ServerFrame {
    @Serializable
    @SerialName("auth_ok")
    data class AuthOk(val version: Int) : ServerFrame()

    @Serializable
    @SerialName("auth_fail")
    data class AuthFail(val reason: String) : ServerFrame()

    @Serializable
    @SerialName("pong")
    data object Pong : ServerFrame()

    @Serializable
    @SerialName("error")
    data class Error(val code: Int, val msg: String) : ServerFrame()
}
