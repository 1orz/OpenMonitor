package com.cloudorz.openmonitor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "power_stat_records",
    foreignKeys = [
        ForeignKey(
            entity = PowerStatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class PowerStatRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val capacity: Int,
    val isCharging: Boolean,
    val startTime: Long,
    val endTime: Long,
    val isFuzzy: Boolean,
    val ioBytes: Long,
    val packageName: String,
    val isScreenOn: Boolean,
)
