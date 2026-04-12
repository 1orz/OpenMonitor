package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.DisplayDataSource
import com.cloudorz.openmonitor.core.model.display.DisplayInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayRepository @Inject constructor(
    private val displayDataSource: DisplayDataSource,
) {
    suspend fun getDisplayInfo(): DisplayInfo = displayDataSource.getDisplayInfo()
}
