package com.cloudorz.openmonitor.feature.floatmonitor

import androidx.annotation.StringRes
import com.cloudorz.openmonitor.core.ui.R

enum class FloatMonitorType(
    @param:StringRes val displayNameRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val infoRes: Int,
) {
    LOAD_MONITOR(R.string.monitor_load, R.string.monitor_load_desc, R.string.monitor_load_info),
    PROCESS_MONITOR(R.string.monitor_process, R.string.monitor_process_desc, R.string.monitor_process_info),
    THREAD_MONITOR(R.string.monitor_thread, R.string.monitor_thread_desc, R.string.monitor_thread_info),
    MINI_MONITOR(R.string.monitor_mini, R.string.monitor_mini_desc, R.string.monitor_mini_info),
    FPS_RECORDER(R.string.monitor_fps, R.string.monitor_fps_desc, R.string.monitor_fps_info),
    TEMPERATURE_MONITOR(R.string.monitor_temperature, R.string.monitor_temperature_desc, R.string.monitor_temperature_info),
}
