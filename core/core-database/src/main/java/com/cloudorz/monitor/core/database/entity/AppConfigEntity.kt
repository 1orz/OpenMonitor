package com.cloudorz.monitor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val aloneLight: Boolean,
    val aloneLightValue: Int,
    val disNotice: Boolean,
    val disButton: Boolean,
    val gpsOn: Boolean,
    val freeze: Boolean,
    val screenOrientation: Int,
    val showMonitor: Boolean,
)
