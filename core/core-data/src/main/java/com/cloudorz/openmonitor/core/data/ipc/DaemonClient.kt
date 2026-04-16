package com.cloudorz.openmonitor.core.data.ipc

import android.content.Context
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
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TCP client for the Rust openmonitor-server daemon on localhost:9876.
 *
 * Wire protocol: `[u32 big-endian payload_len] [payload_len bytes UTF-8 JSON]`.
 * After connect, the server writes an `auth_ok` frame; then the client sends
 * `subscribe` and reads snapshot frames in a loop.
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
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val SERVER_PORT = 9876
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null
    private var socket: Socket? = null
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

    /** Try to connect to the server on localhost. Returns true on success. */
    suspend fun connect(): Boolean {
        disconnect()

        XLog.tag(TAG).i("connecting to 127.0.0.1:$SERVER_PORT")
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress("127.0.0.1", SERVER_PORT), CONNECT_TIMEOUT_MS)

            val din = DataInputStream(sock.inputStream)
            val dout = DataOutputStream(sock.outputStream)

            val authPayload = readFrame(din)
            if (authPayload == null) {
                XLog.tag(TAG).w("server closed before auth frame")
                runCatching { sock.close() }
                return false
            }

            val element = try {
                json.parseToJsonElement(authPayload)
            } catch (e: Exception) {
                XLog.tag(TAG).w("failed to parse auth frame: ${e.message}")
                runCatching { sock.close() }
                return false
            }

            when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
                "auth_ok" -> {
                    val frame = json.decodeFromJsonElement(ServerFrame.AuthOk.serializer(), element)
                    if (frame.version != PROTOCOL_VERSION) {
                        XLog.tag(TAG).w("version mismatch server=${frame.version} client=$PROTOCOL_VERSION")
                    }
                    XLog.tag(TAG).i("connected to 127.0.0.1:$SERVER_PORT (server v${frame.version})")
                }
                "auth_fail" -> {
                    val frame = json.decodeFromJsonElement(ServerFrame.AuthFail.serializer(), element)
                    XLog.tag(TAG).w("auth_fail reason=${frame.reason}")
                    runCatching { sock.close() }
                    return false
                }
                else -> {
                    XLog.tag(TAG).w("unexpected first frame type: $type")
                    runCatching { sock.close() }
                    return false
                }
            }

            socket = sock
            dataIn = din
            dataOut = dout
            _connected.value = true

            sendCmd("""{"cmd":"subscribe","interval_ms":$SUBSCRIBE_INTERVAL_MS}""")
            startReader()
            return true
        } catch (e: IOException) {
            XLog.tag(TAG).d("connect failed: ${e.message}")
        } catch (e: Exception) {
            XLog.tag(TAG).w("unexpected error: ${e.message}")
        }
        return false
    }

    fun requestExit() {
        try {
            sendCmd("""{"cmd":"exit"}""")
        } catch (e: Exception) {
            XLog.tag(TAG).d("requestExit failed (best-effort): ${e.message}")
        }
    }

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

                        else -> {
                            XLog.tag(TAG).d("ignoring frame type: $type")
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
