package com.cloudorz.openmonitor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_stat_sessions")
data class ChargeStatSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val beginTime: Long,
    val endTime: Long,
    val capacityRatio: Int,
    val capacityWh: Double,
)
