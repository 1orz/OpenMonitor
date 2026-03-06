package com.cloudorz.monitor.core.data.datasource

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ChoreographerFpsMonitor {

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private var running = false
    private var windowStartNanos = 0L
    private var lastFrameNanos = 0L
    private var renderedFrames = 0
    private var droppedFrames = 0
    private val vsyncPeriodNs = 16_666_667L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            if (lastFrameNanos > 0) {
                val gapNs = frameTimeNanos - lastFrameNanos
                val periods = (gapNs.toDouble() / vsyncPeriodNs).toInt().coerceAtLeast(1)
                renderedFrames++
                droppedFrames += (periods - 1).coerceAtLeast(0)
            }
            lastFrameNanos = frameTimeNanos

            val elapsed = frameTimeNanos - windowStartNanos
            if (elapsed >= 300_000_000L) {
                val totalSlots = renderedFrames + droppedFrames
                val fps = if (totalSlots > 0 && elapsed > 0) {
                    (renderedFrames * 1_000_000_000L / elapsed).toInt()
                } else {
                    0
                }
                _fps.value = fps
                renderedFrames = 0
                droppedFrames = 0
                windowStartNanos = frameTimeNanos
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        renderedFrames = 0
        droppedFrames = 0
        lastFrameNanos = 0L
        windowStartNanos = System.nanoTime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        _fps.value = 0
    }
}
