package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.MemoryDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDataSource: MemoryDataSource
) {
    fun observeMemoryInfo(intervalMs: Long = 2000L): Flow<MemoryInfo> =
        pollingFlow(intervalMs) { memoryDataSource.getMemoryInfo() }

    fun observeSwapInfo(intervalMs: Long = 3000L): Flow<SwapInfo> =
        pollingFlow(intervalMs) { memoryDataSource.getSwapInfo() }

    suspend fun getMemoryInfo(): MemoryInfo = memoryDataSource.getMemoryInfo()
    suspend fun getSwapInfo(): SwapInfo = memoryDataSource.getSwapInfo()
}
