package com.cloudorz.monitor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charge_stat_records",
    foreignKeys = [
        ForeignKey(
            entity = ChargeStatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ChargeStatRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val capacity: Int,
    val currentMa: Long,
    val temperature: Float,
    val powerW: Double,
    val timestamp: Long,
)
