package com.cloudorz.monitor.core.model.fps

enum class FpsMethod(
    val displayName: String,
    val description: String,
    val requiresShell: Boolean,
) {
    DAEMON(
        displayName = "Daemon",
        description = "monitor-daemon 直连，最准确，需 Root/Shizuku",
        requiresShell = true,
    ),
    SURFACE_FLINGER(
        displayName = "SurfaceFlinger",
        description = "dumpsys SurfaceFlinger，需 Root/Shizuku/ADB，可测任意应用",
        requiresShell = true,
    ),
    FRAME_METRICS(
        displayName = "FrameMetrics",
        description = "Window FrameMetrics，无需特权，仅测自身进程",
        requiresShell = false,
    ),
    CHOREOGRAPHER(
        displayName = "Choreographer",
        description = "VSYNC 回调计数，仅反映刷新率（通常恒定），不推荐",
        requiresShell = false,
    ),
}
