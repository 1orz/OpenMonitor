package com.cloudorz.monitor.core.model.thermal

data class ThermalZone(
    val index: Int = 0,
    val name: String = "",
    val type: String = "",
    val temperatureCelsius: Double = 0.0,
) {
    val temperatureFahrenheit: Double
        get() = (temperatureCelsius * 9.0 / 5.0) + 32.0

    val isCritical: Boolean
        get() = temperatureCelsius >= CRITICAL_THRESHOLD_CELSIUS

    val isWarning: Boolean
        get() = temperatureCelsius >= WARNING_THRESHOLD_CELSIUS

    companion object {
        const val CRITICAL_THRESHOLD_CELSIUS = 90.0
        const val WARNING_THRESHOLD_CELSIUS = 70.0
    }
}
