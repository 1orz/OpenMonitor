package com.cloudorz.openmonitor.core.model.fps

data class FpsData(
    val fps: Double = 0.0,
    val jankCount: Int = 0,
    val bigJankCount: Int = 0,
    val maxFrameTimeMs: Int = 0,
    val frameTimesMs: IntArray = intArrayOf(),
    val window: String = "",
) {
    val frameCount: Int
        get() = frameTimesMs.size

    val avgFrameTimeMs: Double
        get() = if (frameTimesMs.isEmpty()) 0.0 else frameTimesMs.average()

    val jankRate: Double
        get() = if (frameCount > 0) (jankCount.toDouble() / frameCount) * 100.0 else 0.0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FpsData) return false
        return fps == other.fps &&
            jankCount == other.jankCount &&
            bigJankCount == other.bigJankCount &&
            maxFrameTimeMs == other.maxFrameTimeMs &&
            frameTimesMs.contentEquals(other.frameTimesMs) &&
            window == other.window
    }

    override fun hashCode(): Int {
        var result = fps.hashCode()
        result = 31 * result + jankCount
        result = 31 * result + bigJankCount
        result = 31 * result + maxFrameTimeMs
        result = 31 * result + frameTimesMs.contentHashCode()
        result = 31 * result + window.hashCode()
        return result
    }
}
