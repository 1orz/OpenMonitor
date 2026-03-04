package com.cloudorz.monitor.feature.floatmonitor

enum class FloatMonitorType(val displayName: String, val description: String) {
    LOAD_MONITOR("负载监视器", "CPU/GPU/RAM/电池仪表盘"),
    PROCESS_MONITOR("进程监视器", "Top CPU 进程列表"),
    THREAD_MONITOR("线程监视器", "当前应用最耗CPU线程"),
    MINI_MONITOR("迷你监视器", "单行: CPU%/GPU%/温度"),
    FPS_RECORDER("帧率记录器", "实时 FPS 计数器"),
    TEMPERATURE_MONITOR("温度监视器", "多传感器温度值"),
}
