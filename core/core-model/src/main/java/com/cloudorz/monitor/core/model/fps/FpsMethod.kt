package com.cloudorz.monitor.core.model.fps

enum class FpsMethod(
    val displayName: String,
    val description: String,
) {
    DAEMON(
        displayName = "Daemon",
        description = "monitor-daemon 直连，需 Root/Shizuku",
    ),
}
