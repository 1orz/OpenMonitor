package com.cloudorz.openmonitor.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.cloudorz.openmonitor.R

sealed class Route(
    val route: String,
    @param:StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    data object Features : Route("features", R.string.nav_features, Icons.Outlined.Dashboard)
    data object Overview : Route("overview", R.string.nav_overview, Icons.Outlined.Monitor)
    data object Settings : Route("settings", R.string.nav_settings, Icons.Outlined.Settings)

    companion object {
        val all: List<Route> = listOf(Features, Overview, Settings)
    }
}

/** Sub-routes reachable from the Features tab. */
object FeatureRoute {
    const val BATTERY = "features/battery"
    const val FPS = "features/fps"
    const val PROCESS = "features/process"
    const val FLOAT = "features/float"
    const val SENSOR = "features/sensor"
    const val NETWORK = "features/network"
    const val LOG = "features/log"
    const val HARDWARE = "features/hardware"
    const val CPU_ANALYSIS = "features/hardware/cpu-analysis"
    const val VULKAN_INFO = "features/hardware/vulkan-info"
    const val OPENGL_INFO = "features/hardware/opengl-info"
    const val PARTITIONS = "features/hardware/partitions"
    const val FPS_SESSION_DETAIL = "features/fps/session/{sessionId}"
    const val PROCESS_DETAIL = "features/process/{pid}"
    const val KEY_ATTESTATION = "features/key-attestation"
    const val LICENSES = "settings/licenses"
    const val LICENSE_DETAIL = "settings/licenses/{index}"

    fun fpsSessionDetail(sessionId: String) = "features/fps/session/$sessionId"
    fun licenseDetail(index: Int) = "settings/licenses/$index"
    fun processDetail(pid: Int) = "features/process/$pid"
}
