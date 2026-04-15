package com.cloudorz.openmonitor.core.model.fps

enum class FpsMethod(
    val displayName: String,
    val description: String,
) {
    SERVER(
        displayName = "Server",
        description = "Privileged server via shared memory, requires Root/Shizuku",
    ),
}
