package com.cloudorz.openmonitor.core.data.datasource

import android.util.Log
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ProcessState
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val shellExecutor: ShellExecutor,
    private val appInfoResolver: AppInfoResolver,
    private val daemonClient: DaemonClient,
) {
    companion object {
        private const val TAG = "ProcessDataSource"
    }

    // Android app user pattern: u0_a123, u10_a456 etc.
    private val androidUserPattern = Regex("u\\d+_a\\d+")

    // Valid Java package name: at least two dot-separated alphanumeric segments, no slashes
    private val packageNamePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    suspend fun getProcessList(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        // Daemon (shell uid + readproc) sees all processes; prefer it when available.
        tryGetProcessListFromDaemon()
            ?: getProcessListFromShell()
    }

    private fun tryGetProcessListFromDaemon(): List<ProcessInfo>? {
        val raw = daemonClient.sendCommand("processes") ?: return null
        return parseDaemonProcessList(raw)
    }

    private fun parseDaemonProcessList(json: String): List<ProcessInfo>? {
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<ProcessInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pid = obj.getInt("pid")
                val ppid = obj.optInt("ppid")
                val name = obj.optString("name")
                val stateChar = obj.optString("state", "?").firstOrNull() ?: '?'
                val state = ProcessState.fromCode(stateChar)
                val user = obj.optString("user")
                val cpuPercent = obj.optDouble("cpu_percent", 0.0)
                val rssKB = obj.optLong("rss_kb")
                val swapKB = obj.optLong("swap_kb")
                val shrKB = obj.optLong("shr_kb")
                val cmdline = obj.optString("cmdline")
                val oomAdj = obj.optInt("oom_adj")
                val oomScore = obj.optInt("oom_score")
                val oomScoreAdj = obj.optInt("oom_score_adj")
                val cgroup = obj.optString("cgroup")
                val cpuSet = obj.optString("cpu_set")
                val ctxtSwitches = obj.optLong("ctxt_switches")

                // Detect Android app processes: user matches u0_aXXX.
                // Always prefer cmdline for package name — /proc/status Name is truncated to 15 chars
                // (TASK_COMM_LEN), which breaks matching for packages like "com.android.systemui".
                // Validate against packageNamePattern to avoid false positives (e.g. zsh launched
                // under Termux UID whose cmdline path contains dots but is not a package name).
                val packageName = when {
                    androidUserPattern.matches(user) && cmdline.contains('.') -> {
                        val candidate = cmdline.substringBefore(':').substringBefore(' ').trim()
                        if (packageNamePattern.matches(candidate)) candidate else ""
                    }
                    androidUserPattern.matches(user) && name.contains('.') -> {
                        val candidate = name.substringBefore(':')
                        if (packageNamePattern.matches(candidate)) candidate else ""
                    }
                    else -> ""
                }

                // friendlyName: use first segment of cmdline path (stripped of dir prefix),
                // fall back to name for kernel threads / processes without cmdline.
                val friendlyName = cmdline.substringBefore(' ').substringAfterLast('/').ifEmpty { name }

                list.add(
                    enrichWithAppInfo(
                        ProcessInfo(
                            pid = pid,
                            ppid = ppid,
                            name = name,
                            state = state,
                            user = user,
                            cpuPercent = cpuPercent,
                            rssKB = rssKB,
                            swapKB = swapKB,
                            shrKB = shrKB,
                            cmdline = cmdline,
                            friendlyName = friendlyName,
                            command = cmdline,
                            oomAdj = oomAdj,
                            oomScore = oomScore,
                            oomScoreAdj = oomScoreAdj,
                            cGroup = cgroup,
                            cpuSet = cpuSet,
                            cpusAllowed = cpuSet,
                            ctxtSwitches = ctxtSwitches,
                            packageName = packageName,
                        )
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.d(TAG, "parseDaemonProcessList failed: ${e.message}")
            null
        }
    }

    private suspend fun getProcessListFromShell(): List<ProcessInfo> {
        val result = shellExecutor.execute("ps -A -o PID,PPID,USER,%CPU,RSS,NAME --sort=-%cpu")
        if (!result.isSuccess) return emptyList()
        return result.stdout.lines()
            .drop(1) // header
            .filter { it.isNotBlank() }
            .take(200)
            .mapNotNull { parsePsLine(it) }
            .map { enrichWithAppInfo(it) }
    }

    private fun parsePsLine(line: String): ProcessInfo? {
        val parts = line.trim().split("\\s+".toRegex(), limit = 6)
        if (parts.size < 6) return null
        val user = parts[2]
        val name = parts[5]

        val isApp = androidUserPattern.matches(user) && name.contains('.')
        val packageName = if (isApp) {
            val candidate = name.substringBefore(":")
            if (packageNamePattern.matches(candidate)) candidate else ""
        } else ""

        return ProcessInfo(
            pid = parts[0].toIntOrNull() ?: return null,
            ppid = parts[1].toIntOrNull() ?: 0,
            user = user,
            cpuPercent = parts[3].toDoubleOrNull() ?: 0.0,
            rssKB = parts[4].toLongOrNull() ?: 0L,
            name = name,
            packageName = packageName,
        )
    }

    private fun enrichWithAppInfo(process: ProcessInfo): ProcessInfo {
        if (process.packageName.isEmpty()) return process
        val label = appInfoResolver.resolveLabel(process.packageName)
        return if (label.isNotEmpty()) process.copy(appLabel = label) else process
    }

    suspend fun getProcessDetail(pid: Int): ProcessInfo? = withContext(Dispatchers.IO) {
        val statusLines = sysfsReader.readLines("/proc/$pid/status")
        if (statusLines.isEmpty()) return@withContext null
        val statusMap = mutableMapOf<String, String>()
        for (line in statusLines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) statusMap[parts[0].trim()] = parts[1].trim()
        }

        val cmdline = sysfsReader.readString("/proc/$pid/cmdline")
            ?.replace('\u0000', ' ')?.trim() ?: ""
        val name = statusMap["Name"] ?: ""
        val state = statusMap["State"]?.firstOrNull()?.let { parseState(it) } ?: ProcessState.UNKNOWN
        val ppid = statusMap["PPid"]?.toIntOrNull() ?: 0
        val rssPages = statusMap["VmRSS"]?.split("\\s+".toRegex())?.firstOrNull()?.toLongOrNull() ?: 0L
        val swapKB = statusMap["VmSwap"]?.split("\\s+".toRegex())?.firstOrNull()?.toLongOrNull() ?: 0L
        val cpuSet = statusMap["Cpus_allowed_list"] ?: ""
        val ctxtSwitches = statusMap["voluntary_ctxt_switches"] ?: ""

        val oomAdj = sysfsReader.readString("/proc/$pid/oom_adj") ?: ""
        val oomScore = sysfsReader.readString("/proc/$pid/oom_score") ?: ""
        val oomScoreAdj = sysfsReader.readString("/proc/$pid/oom_score_adj") ?: ""
        val cGroup = sysfsReader.readString("/proc/$pid/cgroup") ?: ""

        ProcessInfo(
            pid = pid,
            ppid = ppid,
            name = name,
            state = state,
            command = cmdline,
            cmdline = cmdline,
            friendlyName = cmdline.split("/").lastOrNull() ?: name,
            rssKB = rssPages,
            swapKB = swapKB,
            cpuSet = cpuSet,
            cpusAllowed = cpuSet,
            ctxtSwitches = ctxtSwitches.toLongOrNull() ?: 0L,
            oomAdj = oomAdj.toIntOrNull() ?: 0,
            oomScore = oomScore.toIntOrNull() ?: 0,
            oomScoreAdj = oomScoreAdj.toIntOrNull() ?: 0,
            cGroup = cGroup,
            user = statusMap["Uid"]?.split("\\s+".toRegex())?.firstOrNull() ?: ""
        )
    }

    suspend fun getThreads(pid: Int): List<ThreadInfo> = withContext(Dispatchers.IO) {
        // Daemon provides accurate thread CPU% via /proc/<pid>/task reads.
        tryGetThreadsFromDaemon(pid)
            ?: getThreadsFallback(pid)
    }

    private fun tryGetThreadsFromDaemon(pid: Int): List<ThreadInfo>? {
        val raw = daemonClient.sendCommand("threads/$pid") ?: return null
        return parseDaemonThreads(raw)
    }

    private fun parseDaemonThreads(json: String): List<ThreadInfo>? {
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) return emptyList()
            val list = ArrayList<ThreadInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ThreadInfo(
                        tid = obj.getInt("tid"),
                        name = obj.optString("name"),
                        cpuLoadPercent = obj.optDouble("cpu_percent", 0.0),
                    )
                )
            }
            list.sortedByDescending { it.cpuLoadPercent }
        } catch (e: Exception) {
            Log.d(TAG, "parseDaemonThreads failed: ${e.message}")
            null
        }
    }

    private suspend fun getThreadsFallback(pid: Int): List<ThreadInfo> = withContext(Dispatchers.IO) {
        try {
            java.io.File("/proc/$pid/task").list()
                ?.mapNotNull { tidStr ->
                    val tid = tidStr.toIntOrNull() ?: return@mapNotNull null
                    val name = sysfsReader.readString("/proc/$pid/task/$tid/comm")?.trim() ?: ""
                    ThreadInfo(tid = tid, name = name)
                }
                ?: emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "getThreadsFallback failed for pid=$pid: ${e.message}")
            emptyList()
        }
    }

    private fun parseState(c: Char): ProcessState = when (c) {
        'R' -> ProcessState.RUNNING
        'S' -> ProcessState.SLEEPING
        'D' -> ProcessState.DISK_SLEEP
        'T' -> ProcessState.STOPPED
        't' -> ProcessState.TRACING
        'Z' -> ProcessState.ZOMBIE
        'X' -> ProcessState.DEAD
        else -> ProcessState.UNKNOWN
    }

    suspend fun getTopProcesses(count: Int = 5): List<ProcessInfo> {
        return getProcessList().take(count)
    }
}
