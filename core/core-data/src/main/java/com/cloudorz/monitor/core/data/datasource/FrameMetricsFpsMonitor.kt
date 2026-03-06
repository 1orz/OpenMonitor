package com.cloudorz.monitor.core.data.datasource

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import com.cloudorz.monitor.core.model.fps.FpsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FrameMetricsFpsMonitor {

    private val _fpsData = MutableStateFlow(FpsData())
    val fpsData: StateFlow<FpsData> = _fpsData

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private var running = false
    private var currentWindow: Window? = null
    private val handler = Handler(Looper.getMainLooper())

    private val frameTimes = mutableListOf<Int>()
    private var windowStartMs = System.currentTimeMillis()

    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        if (!running) return@OnFrameMetricsAvailableListener

        val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val frameTimeMs = (totalDurationNs / 1_000_000).toInt()
        if (frameTimeMs in 1..1000) {
            synchronized(frameTimes) {
                frameTimes.add(frameTimeMs)
            }
        }

        val now = System.currentTimeMillis()
        if (now - windowStartMs >= 300L) {
            flush(now)
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (running) attachToWindow(activity.window)
        }
        override fun onActivityPaused(activity: Activity) {
            if (activity.window == currentWindow) detachFromWindow()
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private var application: Application? = null

    fun start(app: Application) {
        if (running) return
        running = true
        application = app
        windowStartMs = System.currentTimeMillis()
        synchronized(frameTimes) { frameTimes.clear() }
        app.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    fun stop() {
        if (!running) return
        running = false
        detachFromWindow()
        application?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        application = null
        _fps.value = 0
        _fpsData.value = FpsData()
    }

    private fun attachToWindow(window: Window) {
        detachFromWindow()
        currentWindow = window
        try {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        } catch (_: Exception) {}
    }

    private fun detachFromWindow() {
        currentWindow?.let { win ->
            try {
                win.removeOnFrameMetricsAvailableListener(listener)
            } catch (_: Exception) {}
        }
        currentWindow = null
    }

    private fun flush(now: Long) {
        val snapshot: List<Int>
        synchronized(frameTimes) {
            snapshot = frameTimes.toList()
            frameTimes.clear()
        }

        val elapsedMs = now - windowStartMs
        windowStartMs = now

        if (snapshot.isEmpty() || elapsedMs <= 0) {
            _fps.value = 0
            _fpsData.value = FpsData()
            return
        }

        val fps = (snapshot.size * 1000L / elapsedMs).toInt()
        val jankThresholdMs = 33
        val bigJankThresholdMs = 50
        val jankCount = snapshot.count { it > jankThresholdMs }
        val bigJankCount = snapshot.count { it > bigJankThresholdMs }
        val maxFrameTime = snapshot.maxOrNull() ?: 0

        _fps.value = fps
        _fpsData.value = FpsData(
            fps = fps,
            jankCount = jankCount,
            bigJankCount = bigJankCount,
            maxFrameTimeMs = maxFrameTime,
            frameTimesMs = snapshot.toIntArray(),
            window = "self",
        )
    }
}
