package com.cloudorz.openmonitor.core.database

import android.content.Context
import androidx.room.Room
import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.dao.ChargeStatDao
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MonitorDatabase {
        return Room.databaseBuilder(
            context,
            MonitorDatabase::class.java,
            "cloud_monitor.db",
        ).addMigrations(MonitorDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun providePowerStatDao(db: MonitorDatabase): PowerStatDao {
        return db.powerStatDao()
    }

    @Provides
    fun provideChargeStatDao(db: MonitorDatabase): ChargeStatDao {
        return db.chargeStatDao()
    }

    @Provides
    fun provideFpsSessionDao(db: MonitorDatabase): FpsSessionDao {
        return db.fpsSessionDao()
    }

    @Provides
    fun provideBatteryRecordDao(db: MonitorDatabase): BatteryRecordDao {
        return db.batteryRecordDao()
    }
}
