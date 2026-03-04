package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.CpuDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.cpu.CpuGlobalStatus
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

    suspend fun setGovernor(policyIndex: Int, governor: String): Boolean {
        val path = "/sys/devices/system/cpu/cpufreq/policy$policyIndex/scaling_governor"
        return cpuDataSource.run {
            // Delegate to SysfsReader through DataSource (will be connected in DI)
            true // placeholder
        }
    }
}
