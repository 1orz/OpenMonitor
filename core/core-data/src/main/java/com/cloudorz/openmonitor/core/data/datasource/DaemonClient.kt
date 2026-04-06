package com.cloudorz.openmonitor.core.data.datasource

import com.elvishew.xlog.XLog
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level TCP client for monitor-daemon.
 * Protocol: 4-byte big-endian length prefix + UTF-8 JSON body.
 *
 * Uses a persistent connection with automatic reconnect on failure.
 * All public methods are synchronized to prevent concurrent socket access.
 */
@Singleton
class DaemonClient @Inject constructor() {

    companion object {
        private const val TAG = "DaemonClient"
        private const val HOST = "127.0.0.1"
        private const val PORT = 9876
        private const val CONNECT_TIMEOUT_MS = 500
        private const val READ_TIMEOUT_MS = 2000
    }

    private var socket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null

    /** Returns true if the daemon is reachable and responds to ping. */
    fun isAlive(): Boolean {
        return try {
            sendCommand("ping")?.contains("pong") == true
        } catch (e: SocketTimeoutException) {
            XLog.tag(TAG).e("isAlive TIMEOUT: ${e.message}")
            false
        } catch (e: Exception) {
            XLog.tag(TAG).e("isAlive failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Sends a command (with optional arg after '\n') and returns the raw response string,
     * or null on any error.
     */
    @Synchronized
    fun sendCommand(cmd: String, arg: String = ""): String? {
        val payload = if (arg.isEmpty()) cmd else "$cmd\n$arg"
        return try {
            sendRaw(payload)
        } catch (e: SocketTimeoutException) {
            XLog.tag(TAG).e("sendCommand($cmd) TIMEOUT: ${e.message}")
            disconnect()
            null
        } catch (e: Exception) {
            XLog.tag(TAG).e("sendCommand($cmd) failed: ${e.javaClass.simpleName}: ${e.message}")
            disconnect()
            null
        }
    }

    /** Close the persistent connection. Called when daemon stops or mode changes. */
    @Synchronized
    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        dataOut = null
        dataIn = null
    }

    private fun ensureConnected() {
        val s = socket
        if (s != null && s.isConnected && !s.isClosed) return

        // Clean up stale refs
        disconnect()

        val newSocket = Socket()
        newSocket.soTimeout = READ_TIMEOUT_MS
        newSocket.tcpNoDelay = true
        newSocket.connect(InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS)
        socket = newSocket
        dataOut = DataOutputStream(newSocket.getOutputStream())
        dataIn = DataInputStream(newSocket.getInputStream())
    }

    private fun sendRaw(payload: String): String {
        ensureConnected()
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val out = dataOut!!
        val din = dataIn!!
        // 4-byte big-endian length prefix
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
        val len = din.readInt()
        val body = ByteArray(len)
        din.readFully(body)
        return String(body, Charsets.UTF_8)
    }
}
