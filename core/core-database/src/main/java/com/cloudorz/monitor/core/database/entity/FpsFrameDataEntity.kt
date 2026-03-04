package com.cloudorz.monitor.core.database.entity

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
)
