package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.data.datasource.ProcessDataSource
import com.cloudorz.monitor.core.data.pollingFlow
import com.cloudorz.monitor.core.model.process.ProcessInfo
import com.cloudorz.monitor.core.model.process.ThreadInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessRepository @Inject constructor(
    private val processDataSource: ProcessDataSource
) {
    fun observeProcessList(intervalMs: Long = 3000L): Flow<List<ProcessInfo>> =
        pollingFlow(intervalMs) { processDataSource.getProcessList() }

    fun observeTopProcesses(count: Int = 5, intervalMs: Long = 2000L): Flow<List<ProcessInfo>> =
        pollingFlow(intervalMs) { processDataSource.getTopProcesses(count) }

    suspend fun getProcessList(): List<ProcessInfo> = processDataSource.getProcessList()
    suspend fun getProcessDetail(pid: Int): ProcessInfo? = processDataSource.getProcessDetail(pid)
    suspend fun getThreads(pid: Int): List<ThreadInfo> = processDataSource.getThreads(pid)
    suspend fun killProcess(pid: Int): Boolean = processDataSource.killProcess(pid)
}
