package com.cloudorz.openmonitor.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MonitorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("monitor_settings", MODE_PRIVATE)
        val serviceActive = prefs.getBoolean("float_service_active", false)

        if (serviceActive) {
            startService(FloatMonitorService.showPanelIntent(this))
        } else {
            startForegroundService(FloatMonitorService.startIntent(this))
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("monitor_settings", MODE_PRIVATE)
        val active = prefs.getBoolean("float_service_active", false)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
