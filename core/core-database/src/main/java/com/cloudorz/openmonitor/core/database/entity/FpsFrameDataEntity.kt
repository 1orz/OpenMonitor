package com.cloudorz.openmonitor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fps_frame_data",
    foreignKeys = [
        ForeignKey(
            entity = FpsSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class FpsFrameDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val fps: Double,
    val jankCount: Int,
    val bigJankCount: Int,
    val maxFrameTimeMs: Int,
    val frameTimesJson: String,
    // System metrics collected alongside FPS
    val cpuLoad: Double = 0.0,
    val cpuTemp: Double = 0.0,
    val gpuLoad: Double = 0.0,
    val gpuTemp: Double = 0.0,
    val gpuFreqMhz: Int = 0,
    val batteryCapacity: Int = 0,
    val batteryCurrentMa: Int = 0,
    val batteryTemp: Double = 0.0,
    val powerW: Double = 0.0,
    val cpuCoreLoadsJson: String = "",
    val cpuCoreFreqsJson: String = "",
    val packageName: String = "",
)
