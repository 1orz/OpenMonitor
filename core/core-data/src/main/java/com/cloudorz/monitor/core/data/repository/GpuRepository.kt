package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.GpuDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.gpu.GpuInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuRepository @Inject constructor(
    private val gpuDataSource: GpuDataSource
) {
    fun observeGpuInfo(intervalMs: Long = 1000L): Flow<GpuInfo> =
        pollingFlow(intervalMs) { gpuDataSource.getGpuInfo() }

    suspend fun getGpuInfo(): GpuInfo = gpuDataSource.getGpuInfo()
}
