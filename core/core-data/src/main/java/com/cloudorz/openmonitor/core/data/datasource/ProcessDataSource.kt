package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ProcessState
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val shellExecutor: ShellExecutor,
    private val appInfoResolver: AppInfoResolver,
) {
    // Android app user pattern: u0_a123, u10_a456 etc.
    private val androidUserPattern = Regex("u\\d+_a\\d+")

    suspend fun getProcessList(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("ps -A -o PID,PPID,USER,%CPU,RSS,NAME --sort=-%cpu")
        if (!result.isSuccess) return@withContext emptyList()

        result.stdout.lines()
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

        // Android app detection: user matches u0_aXXX pattern and name has dot (package name)
        val isApp = androidUserPattern.matches(user) && name.contains('.')
        val packageName = if (isApp) name.substringBefore(":") else ""

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
        val result = shellExecutor.execute("ls /proc/$pid/task")
        if (!result.isSuccess) return@withContext emptyList()

        result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { tidStr ->
                val tid = tidStr.trim().toIntOrNull() ?: return@mapNotNull null
                val name = sysfsReader.readString("/proc/$pid/task/$tid/comm")?.trim() ?: ""
                ThreadInfo(tid = tid, name = name)
            }
    }

    /**
     * 使用 top -H 获取线程列表及 CPU 使用率，失败时回退到 [getThreads]。
     */
    suspend fun getThreadsWithCpu(pid: Int): List<ThreadInfo> = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("top -H -b -q -n 1 -p $pid -o TID,%CPU,CMD")
        if (!result.isSuccess || result.stdout.isBlank()) return@withContext getThreads(pid)

        val threads = result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val trimmed = line.trim()
                val cols = trimmed.split("\\s+".toRegex(), limit = 3)
                if (cols.size < 2) return@mapNotNull null
                val tid = cols[0].toIntOrNull() ?: return@mapNotNull null
                val cpuLoad = cols[1].toDoubleOrNull() ?: 0.0
                val name = if (cols.size >= 3) cols[2].trim() else ""
                ThreadInfo(tid = tid, name = name, cpuLoadPercent = cpuLoad)
            }
            .sortedByDescending { it.cpuLoadPercent }

        threads.ifEmpty { getThreads(pid) }
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
