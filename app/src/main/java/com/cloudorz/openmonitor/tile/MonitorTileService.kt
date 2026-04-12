package com.cloudorz.openmonitor.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("StartActivityAndCollapseDeprecated", "DEPRECATION")
            startActivityAndCollapse(intent)
        }
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
        val tempStr = if (cpuTemp != null) "${cpuTemp.toInt()}" else "--"

        tile.label = getString(R.string.tile_label_format, loadPercent, tempStr)
        tile.state = Tile.STATE_ACTIVE

        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_monitor)

        tile.updateTile()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
    }
}
