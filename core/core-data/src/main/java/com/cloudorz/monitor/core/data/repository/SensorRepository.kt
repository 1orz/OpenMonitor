package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.SensorDataSource
import com.cloudorz.monitor.core.model.sensor.SensorInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorRepository @Inject constructor(
    private val sensorDataSource: SensorDataSource,
) {
    fun observeSensors(): Flow<List<SensorInfo>> {
        val sensors = sensorDataSource.getAllSensors()
        return sensorDataSource.observeSensorValues().map { valuesMap ->
            sensors.map { sensor ->
                SensorInfo(
                    name = sensor.name,
                    type = sensor.type,
                    typeName = SensorDataSource.sensorTypeName(sensor.type),
                    vendor = sensor.vendor,
                    version = sensor.version,
                    resolution = sensor.resolution,
                    maxRange = sensor.maximumRange,
                    power = sensor.power,
                    minDelay = sensor.minDelay,
                    values = valuesMap[sensor.type] ?: floatArrayOf(),
                )
            }
        }
    }

    fun getSensorList(): List<SensorInfo> {
        return sensorDataSource.getAllSensors().map { sensor ->
            SensorInfo(
                name = sensor.name,
                type = sensor.type,
                typeName = SensorDataSource.sensorTypeName(sensor.type),
                vendor = sensor.vendor,
                version = sensor.version,
                resolution = sensor.resolution,
                maxRange = sensor.maximumRange,
                power = sensor.power,
                minDelay = sensor.minDelay,
            )
        }
    }
}
