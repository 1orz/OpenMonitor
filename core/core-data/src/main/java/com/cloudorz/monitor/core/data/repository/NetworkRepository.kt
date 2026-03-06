package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.NetworkDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.network.NetworkInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    private val networkDataSource: NetworkDataSource,
) {
    fun observeNetworkInfo(intervalMs: Long = 2000L): Flow<NetworkInfo> =
        pollingFlow(intervalMs) { networkDataSource.getNetworkInfo() }

    suspend fun getNetworkInfo(): NetworkInfo = networkDataSource.getNetworkInfo()
}
