package com.cloudorz.openmonitor.core.model.process

enum class ProcessFilterMode {
    ALL,
    APP_ONLY,
}

enum class ProcessState(val code: Char, val displayName: String) {
    RUNNING('R', "Running"),
    SLEEPING('S', "Sleeping"),
    DISK_SLEEP('D', "Disk Sleep"),
    STOPPED('T', "Stopped"),
    TRACING('t', "Tracing"),
    ZOMBIE('Z', "Zombie"),
    DEAD('X', "Dead"),
    UNKNOWN('?', "Unknown");

    companion object {
        fun fromCode(code: Char): ProcessState =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class ProcessInfo(
    val pid: Int = 0,
    val ppid: Int = 0,
    val name: String = "",
    val cpuPercent: Double = 0.0,
    val cpuSet: String = "",
    val cpusAllowed: String = "",
    val ctxtSwitches: Long = 0,
    val memKB: Long = 0,
    val rssKB: Long = 0,
    val shrKB: Long = 0,
    val swapKB: Long = 0,
    val user: String = "",
    val state: ProcessState = ProcessState.UNKNOWN,
    val command: String = "",
    val cmdline: String = "",
    val friendlyName: String = "",
    val cGroup: String = "",
    val oomAdj: Int = 0,
    val oomScore: Int = 0,
    val oomScoreAdj: Int = 0,
    val packageName: String = "",
    val appLabel: String = "",
    val isSystemApp: Boolean = false,
) {
    val isZombie: Boolean
        get() = state == ProcessState.ZOMBIE

    val isRunning: Boolean
        get() = state == ProcessState.RUNNING

    val isAndroidApp: Boolean
        get() = packageName.isNotEmpty()

    /** Non-system user app: has a package name and is not a system-bundled app. */
    val isUserApp: Boolean
        get() = packageName.isNotEmpty() && !isSystemApp

    val displayName: String
        get() = appLabel.ifEmpty { friendlyName.ifEmpty { name.ifEmpty { command } } }

    val memMB: Double
        get() = memKB / 1024.0

    val rssMB: Double
        get() = rssKB / 1024.0
}
