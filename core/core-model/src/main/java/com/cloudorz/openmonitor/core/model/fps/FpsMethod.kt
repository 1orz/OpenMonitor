package com.cloudorz.openmonitor.core.model.fps

enum class FpsMethod(
    val displayName: String,
    val description: String,
) {
    DAEMON(
        displayName = "Daemon",
        description = "monitor-daemon direct connection, requires Root/Shizuku",
    ),
}
