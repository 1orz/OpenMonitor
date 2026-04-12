package com.cloudorz.openmonitor.ui.navigation

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.cloudorz.openmonitor.R
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey and can be saved/restored in the back stack.
 */
sealed interface Route : NavKey, Parcelable {

    // Main screen (tabs + pager)
    @Parcelize @Serializable data object Main : Route

    // Feature screens (no parameters)
    @Parcelize @Serializable data object Hardware : Route
    @Parcelize @Serializable data object CpuAnalysis : Route
    @Parcelize @Serializable data object VulkanInfo : Route
    @Parcelize @Serializable data object OpenGLInfo : Route
    @Parcelize @Serializable data object Partitions : Route
    @Parcelize @Serializable data object Battery : Route
    @Parcelize @Serializable data object Fps : Route
    @Parcelize @Serializable data object Process : Route
    @Parcelize @Serializable data object FloatMonitor : Route
    @Parcelize @Serializable data object Sensor : Route
    @Parcelize @Serializable data object Network : Route
    @Parcelize @Serializable data object KeyAttestation : Route
    @Parcelize @Serializable data object Log : Route
    @Parcelize @Serializable data object ColorPalette : Route
    @Parcelize @Serializable data object Licenses : Route
    @Parcelize @Serializable data object Donate : Route

    // Parameterized screens
    @Parcelize @Serializable
    data class FpsSessionDetail(val sessionId: String) : Route

    @Parcelize @Serializable
    data class ProcessDetail(val pid: String) : Route

    @Parcelize @Serializable
    data class LicenseDetail(val index: Int) : Route

    companion object {
        val tabs = listOf(
            TabItem(R.string.nav_features, Icons.Outlined.Dashboard),
            TabItem(R.string.nav_overview, Icons.Outlined.Monitor),
            TabItem(R.string.nav_settings, Icons.Outlined.Settings),
        )
    }
}

data class TabItem(
    @param:StringRes val labelResId: Int,
    val icon: ImageVector,
)
