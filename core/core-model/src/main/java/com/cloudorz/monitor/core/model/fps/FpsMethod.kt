package com.cloudorz.monitor.core.model.fps

enum class FpsMethod(
    val displayName: String,
    val description: String,
    val requiresShell: Boolean,
) {
    SURFACE_FLINGER(
        displayName = "SurfaceFlinger",
        description = "dumpsys SurfaceFlinger，需 Root/ADB，可测任意应用",
        requiresShell = true,
    ),
    FRAME_METRICS(
        displayName = "FrameMetrics",
        description = "Window FrameMetrics，API 24+，无需 Root，仅测自身进程",
        requiresShell = false,
    ),
    CHOREOGRAPHER(
        displayName = "Choreographer",
        description = "VSYNC 回调计数，仅反映显示刷新率（通常恒为 60），不推荐",
        requiresShell = false,
    ),
}
