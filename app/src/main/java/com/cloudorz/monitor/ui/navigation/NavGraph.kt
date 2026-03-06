package com.cloudorz.monitor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Route(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Features : Route("features", "功能", Icons.Outlined.Dashboard)
    data object Overview : Route("overview", "概览", Icons.Outlined.Monitor)
    data object Control : Route("control", "CPU", Icons.Outlined.Tune)
    data object User : Route("user", "用户", Icons.Outlined.Person)

    companion object {
        val all: List<Route> = listOf(Features, Overview, Control, User)
    }
}

/** Sub-routes reachable from the Features tab. */
object FeatureRoute {
    const val POWER = "features/power"
    const val CHARGE = "features/charge"
    const val FPS = "features/fps"
    const val PROCESS = "features/process"
    const val FLOAT = "features/float"
    const val STORAGE = "features/storage"
    const val SENSOR = "features/sensor"
    const val NETWORK = "features/network"
}
