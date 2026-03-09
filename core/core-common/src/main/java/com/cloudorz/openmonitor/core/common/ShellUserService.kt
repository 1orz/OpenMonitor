package com.cloudorz.openmonitor.core.common

import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService implementation that runs in a separate process with
 * shell (UID 2000) or root (UID 0) privileges.
 *
 * This is NOT an Android Service component — it is launched by Shizuku via
 * `app_process` and communicates with the app via Binder IPC.
 */
class ShellUserService : IShellService.Stub() {

    companion object {
        private const val TAG = "ShellUserService"
    }

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    /**
     * Executes a shell command and returns result as:
     * `exitCode\nSTDOUT_START\n<stdout>\nSTDERR_START\n<stderr>`
     */
    override fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                "ERROR:timeout"
            } else {
                val exitCode = process.exitValue()
                "${exitCode}\nSTDOUT_START\n${stdout}\nSTDERR_START\n${stderr}"
            }
        } catch (e: Exception) {
            "ERROR:${e.message}"
        }
    }

    override fun readFileContent(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText()
            } else {
                // Fall back to cat for files needing elevated access
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat '$path'"))
                val output = process.inputStream.bufferedReader().readText()
                val completed = process.waitFor(10, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    null
                } else if (process.exitValue() == 0) output else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "readFileContent failed: $path", e)
            null
        }
    }

}
