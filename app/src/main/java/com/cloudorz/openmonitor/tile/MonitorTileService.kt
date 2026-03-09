package com.cloudorz.openmonitor.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cloudorz.openmonitor.MainActivity
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.data.datasource.CpuDataSource
import com.cloudorz.openmonitor.core.data.datasource.ThermalDataSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MonitorTileService : TileService() {

    @Inject
    lateinit var cpuDataSource: CpuDataSource

    @Inject
    lateinit var thermalDataSource: ThermalDataSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var refreshJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                updateTileData()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onStopListening() {
        refreshJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun updateTileData() {
        val tile = qsTile ?: return

        val loads = cpuDataSource.getCpuLoad()
        val totalLoad = if (loads.isNotEmpty()) loads[0] else 0.0
        val loadPercent = totalLoad.toInt()

        val cpuTemp = thermalDataSource.getCpuTemperature()
        val tempStr = if (cpuTemp > 0) "${cpuTemp.toInt()}" else "--"

        tile.label = getString(R.string.tile_label_format, loadPercent, tempStr)
        tile.state = Tile.STATE_ACTIVE

        val iconRes = when {
            loadPercent >= 80 -> R.drawable.ic_tile_monitor
            loadPercent >= 50 -> R.drawable.ic_tile_monitor
            else -> R.drawable.ic_tile_monitor
        }
        val tintColor = when {
            loadPercent >= 80 -> 0xFFF44336.toInt() // red
            loadPercent >= 50 -> 0xFFFFC107.toInt() // yellow
            else -> 0xFF4CAF50.toInt() // green
        }
        tile.icon = Icon.createWithResource(this, iconRes)

        tile.updateTile()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
    }
}
