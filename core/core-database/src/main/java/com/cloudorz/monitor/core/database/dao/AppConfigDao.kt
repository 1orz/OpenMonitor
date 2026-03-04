package com.cloudorz.monitor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cloudorz.monitor.core.database.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {

    @Upsert
    suspend fun insertOrUpdate(entity: AppConfigEntity)

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName")
    fun getConfig(packageName: String): Flow<AppConfigEntity?>

    @Query("SELECT * FROM app_configs ORDER BY packageName ASC")
    fun getAllConfigs(): Flow<List<AppConfigEntity>>

    @Query("DELETE FROM app_configs WHERE packageName = :packageName")
    suspend fun deleteConfig(packageName: String)
}
