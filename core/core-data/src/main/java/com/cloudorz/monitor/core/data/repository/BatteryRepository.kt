package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.BatteryDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    private val batteryDataSource: BatteryDataSource
) {
    fun observeBatteryStatus(intervalMs: Long = 5000L): Flow<BatteryStatus> =
        pollingFlow(intervalMs) { batteryDataSource.getBatteryStatus() }

    suspend fun getBatteryStatus(): BatteryStatus = batteryDataSource.getBatteryStatus()

    suspend fun setChargingEnabled(enabled: Boolean): Boolean =
        batteryDataSource.setChargingEnabled(enabled)

    suspend fun setChargeCurrentLimit(limitMa: Int): Boolean =
        batteryDataSource.setChargeCurrentLimit(limitMa)

    suspend fun setNightCharging(enabled: Boolean): Boolean =
        batteryDataSource.setNightCharging(enabled)
}
