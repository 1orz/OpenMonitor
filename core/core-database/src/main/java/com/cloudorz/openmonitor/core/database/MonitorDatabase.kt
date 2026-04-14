package com.cloudorz.openmonitor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.dao.ChargeStatDao
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import com.cloudorz.openmonitor.core.database.entity.BatteryRecordEntity
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
        BatteryRecordEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun powerStatDao(): PowerStatDao
    abstract fun chargeStatDao(): ChargeStatDao
    abstract fun fpsSessionDao(): FpsSessionDao
    abstract fun batteryRecordDao(): BatteryRecordDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS battery_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        capacity INTEGER NOT NULL,
                        currentMa INTEGER NOT NULL,
                        voltageV REAL NOT NULL,
                        powerW REAL NOT NULL,
                        temperatureCelsius REAL NOT NULL,
                        isCharging INTEGER NOT NULL,
                        isScreenOn INTEGER NOT NULL,
                        packageName TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_records_timestamp ON battery_records (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_records_packageName ON battery_records (packageName)")
            }
        }
    }
}
