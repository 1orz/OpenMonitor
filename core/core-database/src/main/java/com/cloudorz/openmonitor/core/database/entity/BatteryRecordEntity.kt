package com.cloudorz.openmonitor.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_records",
    indices = [Index("timestamp"), Index("packageName")],
)
data class BatteryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val capacity: Int,
    val currentMa: Int,
    val voltageV: Double,
    val powerW: Double,
    val temperatureCelsius: Double,
    val isCharging: Boolean,
    val isScreenOn: Boolean,
    val packageName: String,
)
