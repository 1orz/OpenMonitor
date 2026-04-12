package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun getAllSensors(): List<Sensor> =
        sensorManager.getSensorList(Sensor.TYPE_ALL)

    fun observeSensorValues(): Flow<Map<Int, FloatArray>> = callbackFlow {
        val sensors = getAllSensors()
        val latestValues = mutableMapOf<Int, FloatArray>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestValues[event.sensor.type] = event.values.clone()
                trySend(latestValues.toMap())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensors.forEach { sensor ->
            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        fun sensorTypeName(type: Int): String = when (type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_LIGHT -> "Light"
            Sensor.TYPE_PRESSURE -> "Pressure"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_GRAVITY -> "Gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
            Sensor.TYPE_STEP_COUNTER -> "Step Counter"
            Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
            Sensor.TYPE_HEART_RATE -> "Heart Rate"
            else -> "Sensor ($type)"
        }
    }
}
