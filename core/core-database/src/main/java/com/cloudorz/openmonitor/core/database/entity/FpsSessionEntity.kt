package com.cloudorz.openmonitor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fps_sessions")
data class FpsSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val packageName: String,
    val appName: String,
    val avgFps: Double,
    val avgPowerW: Double,
    val beginTime: Long,
    val durationSeconds: Int,
    val mode: String,
    val packageVersion: String,
    val sessionDesc: String,
    val viewSize: String,
)
