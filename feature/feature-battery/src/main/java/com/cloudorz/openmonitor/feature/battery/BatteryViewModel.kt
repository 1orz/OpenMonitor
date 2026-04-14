package com.cloudorz.openmonitor.feature.battery

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.datasource.ForegroundAppDataSource
import com.cloudorz.openmonitor.core.data.repository.BatteryRecordRepository
import com.cloudorz.openmonitor.core.data.repository.BatteryRepository
import com.cloudorz.openmonitor.core.model.battery.AppUsageEntry
import com.cloudorz.openmonitor.core.model.battery.BatteryChartPoint
import com.cloudorz.openmonitor.core.model.battery.BatteryEstimation
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

enum class TimeRange(val label: String, val durationMs: Long) {
    LAST_1H("1h", 3_600_000L),
    LAST_6H("6h", 21_600_000L),
    LAST_24H("24h", 86_400_000L),
    LAST_7D("7d", 604_800_000L),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val batteryRecordRepository: BatteryRecordRepository,
    private val foregroundAppDataSource: ForegroundAppDataSource,
    private val csvExporter: CsvExporter,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val batteryStatus: StateFlow<BatteryStatus> = batteryRepository
        .observeBatteryStatus(3000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryStatus())

    val timeRange = MutableStateFlow(TimeRange.LAST_24H)

    private val timeWindow: StateFlow<Pair<Long, Long>> = timeRange.map {
        val now = System.currentTimeMillis()
        (now - it.durationMs) to now
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), run {
        val now = System.currentTimeMillis()
        (now - TimeRange.LAST_24H.durationMs) to now
    })

    val chartData: StateFlow<List<BatteryChartPoint>> = timeWindow
        .flatMapLatest { (start, end) ->
            batteryRecordRepository.getRecordsForChart(start, end)
        }
        .map { points -> downsample(points, timeRange.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appUsageList: StateFlow<List<AppUsageEntry>> = MutableStateFlow(emptyList<AppUsageEntry>())
        .also { flow ->
            viewModelScope.launch {
                timeWindow.collect { (start, end) ->
                    flow.value = batteryRecordRepository.getAppUsageBreakdown(start, end)
                }
            }
        }

    val estimation: StateFlow<BatteryEstimation> = combine(
        chartData,
        batteryStatus,
    ) { points, battery ->
        computeEstimation(points, battery)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BatteryEstimation(0, 0.0, 0.0, 0),
    )

    val hasUsageStatsPermission: StateFlow<Boolean> = MutableStateFlow(
        foregroundAppDataSource.hasUsageStatsPermission(),
    )

    // Sparkline: last 60 current mA values
    val currentSparkline: StateFlow<List<Int>> = chartData.map { points ->
        points.takeLast(60).map { it.currentMa }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTimeRange(range: TimeRange) {
        timeRange.value = range
    }

    fun refreshPermissionState() {
        (hasUsageStatsPermission as MutableStateFlow).value =
            foregroundAppDataSource.hasUsageStatsPermission()
    }

    fun getExportIntent(onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val (start, end) = timeWindow.value
            val dir = File(appContext.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, "battery_${start}_${end}.csv")
            file.outputStream().use { csvExporter.exportBatteryRecords(start, end, it) }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                null,
            )
            onReady(intent)
        }
    }

    private fun downsample(
        points: List<BatteryChartPoint>,
        range: TimeRange,
    ): List<BatteryChartPoint> {
        if (points.size <= 500) return points
        val step = when (range) {
            TimeRange.LAST_7D -> 10   // ~5 min per point
            TimeRange.LAST_24H -> 4   // ~2 min per point
            TimeRange.LAST_6H -> 2
            TimeRange.LAST_1H -> 1
        }
        if (step <= 1) return points
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    private fun computeEstimation(
        points: List<BatteryChartPoint>,
        battery: BatteryStatus,
    ): BatteryEstimation {
        // Use last 30 minutes of data for estimation
        val now = System.currentTimeMillis()
        val recent = points.filter { it.timestamp > now - 30 * 60 * 1000L }
        if (recent.size < 2) {
            return BatteryEstimation(0, 0.0, 0.0, 0)
        }

        val first = recent.first()
        val last = recent.last()
        val elapsedMinutes = (last.timestamp - first.timestamp) / 60_000.0
        if (elapsedMinutes < 1) {
            return BatteryEstimation(0, 0.0, 0.0, 0)
        }

        val drainRate = (first.capacity - last.capacity) / elapsedMinutes
        val avgPower = recent.map { abs(it.powerW) }.average()
        val screenOnMs = recent.count { it.packageName.isNotEmpty() } * 30_000L

        val remaining = if (battery.isCharging) {
            if (drainRate >= 0) 0 else ((100 - battery.capacity) / abs(drainRate)).toInt()
        } else {
            if (drainRate <= 0) 0 else (battery.capacity / drainRate).toInt()
        }

        return BatteryEstimation(
            remainingMinutes = remaining,
            drainRatePercentPerMinute = drainRate,
            avgPowerW = avgPower,
            screenOnTimeMs = screenOnMs,
        )
    }
}
