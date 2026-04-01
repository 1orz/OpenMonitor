package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.common.CommandResult
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessActionDataSource @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val daemonClient: DaemonClient,
) {
    suspend fun forceStopApp(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        shellExecutor.execute("killall -9 $packageName; am force-stop $packageName; am kill $packageName")
    }

    suspend fun killProcess(pid: Int): CommandResult = withContext(Dispatchers.IO) {
        // Daemon (shell uid) can kill most user processes; fall back to shellExecutor on failure.
        val raw = daemonClient.sendCommand("kill\n$pid")
        if (raw != null) {
            return@withContext try {
                val obj = JSONObject(raw)
                if (obj.has("error")) {
                    CommandResult.failure(obj.getString("error"))
                } else {
                    CommandResult.success("")
                }
            } catch (e: Exception) {
                CommandResult.failure("kill failed: ${e.message}")
            }
        }
        shellExecutor.execute("kill -9 $pid")
    }

    suspend fun killProcessSmart(process: ProcessInfo): CommandResult {
        return if (process.isAndroidApp) {
            forceStopApp(process.packageName)
        } else {
            killProcess(process.pid)
        }
    }
}
