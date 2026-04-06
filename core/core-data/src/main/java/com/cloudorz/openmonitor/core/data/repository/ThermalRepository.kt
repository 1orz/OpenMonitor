package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.ThermalDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalRepository @Inject constructor(
    private val thermalDataSource: ThermalDataSource
) {
    fun observeThermalZones(intervalMs: Long = 3000L): Flow<List<ThermalZone>> =
        pollingFlow(intervalMs) { thermalDataSource.getAllThermalZones() }

    suspend fun getAllThermalZones(): List<ThermalZone> = thermalDataSource.getAllThermalZones()
    suspend fun getCpuTemperature(): Double? = thermalDataSource.getCpuTemperature()
    suspend fun getGpuTemperature(): Double? = thermalDataSource.getGpuTemperature()
}
