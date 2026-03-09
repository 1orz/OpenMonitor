package com.cloudorz.openmonitor.core.data.datasource

import android.util.Log
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
 * Each call opens a new connection (simple & reliable for ~1s polling interval).
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

    /** Returns true if the daemon is reachable and responds to ping. */
    fun isAlive(): Boolean {
        return try {
            sendRaw("ping").contains("pong")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "isAlive TIMEOUT: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "isAlive failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Sends a command (with optional arg after '\n') and returns the raw response string,
     * or null on any error.
     */
    fun sendCommand(cmd: String, arg: String = ""): String? {
        val payload = if (arg.isEmpty()) cmd else "$cmd\n$arg"
        return try {
            sendRaw(payload)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "sendCommand($cmd) TIMEOUT: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "sendCommand($cmd) failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun sendRaw(payload: String): String {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        Socket().use { socket ->
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS)
            val out = DataOutputStream(socket.getOutputStream())
            // 4-byte big-endian length prefix (DataOutputStream.writeInt = big-endian)
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
            val din = DataInputStream(socket.getInputStream())
            val len = din.readInt()
            val body = ByteArray(len)
            din.readFully(body)
            return String(body, Charsets.UTF_8)
        }
    }
}
