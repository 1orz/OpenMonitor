package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.CpuDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuRepository @Inject constructor(
    private val cpuDataSource: CpuDataSource
) {
    fun observeCpuStatus(intervalMs: Long = 1000L): Flow<CpuGlobalStatus> =
        pollingFlow(intervalMs) { cpuDataSource.getGlobalStatus() }

    suspend fun getCpuStatus(): CpuGlobalStatus = cpuDataSource.getGlobalStatus()
}
