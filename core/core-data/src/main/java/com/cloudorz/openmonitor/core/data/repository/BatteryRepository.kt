package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.BatteryDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
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
}
