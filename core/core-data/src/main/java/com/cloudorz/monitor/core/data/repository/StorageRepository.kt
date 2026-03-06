package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.StorageDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.storage.StorageInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storageDataSource: StorageDataSource,
) {
    fun observeStorageInfo(intervalMs: Long = 5000L): Flow<StorageInfo> =
        pollingFlow(intervalMs) { storageDataSource.getStorageInfo() }

    suspend fun getStorageInfo(): StorageInfo = storageDataSource.getStorageInfo()
}
