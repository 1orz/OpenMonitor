package com.cloudorz.monitor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "power_stat_sessions")
data class PowerStatSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val beginTime: Long,
    val endTime: Long,
    val usedPercent: Int,
    val avgPowerW: Double,
)
