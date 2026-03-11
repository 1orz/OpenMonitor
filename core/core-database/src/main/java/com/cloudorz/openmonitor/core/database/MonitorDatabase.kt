package com.cloudorz.openmonitor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cloudorz.openmonitor.core.database.dao.ChargeStatDao
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import com.cloudorz.openmonitor.core.database.entity.ChargeStatRecordEntity
import com.cloudorz.openmonitor.core.database.entity.ChargeStatSessionEntity
import com.cloudorz.openmonitor.core.database.entity.FpsFrameDataEntity
import com.cloudorz.openmonitor.core.database.entity.FpsSessionEntity
import com.cloudorz.openmonitor.core.database.entity.PowerStatRecordEntity
import com.cloudorz.openmonitor.core.database.entity.PowerStatSessionEntity

@Database(
    entities = [
        PowerStatSessionEntity::class,
        PowerStatRecordEntity::class,
        ChargeStatSessionEntity::class,
        ChargeStatRecordEntity::class,
        FpsSessionEntity::class,
        FpsFrameDataEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun powerStatDao(): PowerStatDao
    abstract fun chargeStatDao(): ChargeStatDao
    abstract fun fpsSessionDao(): FpsSessionDao
}
