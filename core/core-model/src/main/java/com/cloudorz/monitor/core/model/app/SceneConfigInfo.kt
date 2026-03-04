package com.cloudorz.monitor.core.model.app

data class SceneConfigInfo(
    val packageName: String = "",
    val aloneLight: Boolean = false,
    val aloneLightValue: Int = -1,
    val disNotice: Boolean = false,
    val disButton: Boolean = false,
    val gpsOn: Boolean = false,
    val freeze: Boolean = false,
    val screenOrientation: Int = ORIENTATION_UNSPECIFIED,
    val showMonitor: Boolean = false,
) {
    companion object {
        const val ORIENTATION_UNSPECIFIED = -1
        const val ORIENTATION_PORTRAIT = 0
        const val ORIENTATION_LANDSCAPE = 1
    }
}
