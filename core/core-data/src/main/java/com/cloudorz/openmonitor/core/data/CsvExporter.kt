package com.cloudorz.openmonitor.core.data

import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.dao.ChargeStatDao
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    private val powerStatDao: PowerStatDao,
    private val chargeStatDao: ChargeStatDao,
    private val fpsSessionDao: FpsSessionDao,
    private val batteryRecordDao: BatteryRecordDao,
) {
    suspend fun exportPowerSession(sessionId: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,capacity,powerW,temperature,isCharging,isScreenOn")
            val records = powerStatDao.getRecordsBySessionOnce(sessionId)
            for (r in records) {
                writer.appendLine(
                    "${r.startTime},${r.capacity},${r.powerW},${r.temperature},${r.isCharging},${r.isScreenOn}"
                )
            }
        }
    }

    suspend fun exportChargeSession(sessionId: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,capacity,currentMa,temperature,powerW")
            val records = chargeStatDao.getRecordsBySessionOnce(sessionId)
            for (r in records) {
                writer.appendLine(
                    "${r.timestamp},${r.capacity},${r.currentMa},${r.temperature},${r.powerW}"
                )
            }
        }
    }

    suspend fun exportBatteryRecords(startMs: Long, endMs: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,capacity,currentMa,voltageV,powerW,temperatureCelsius,isCharging,isScreenOn,packageName")
            val records = batteryRecordDao.getRecordsInRangeOnce(startMs, endMs)
            for (r in records) {
                writer.appendLine(
                    "${r.timestamp},${r.capacity},${r.currentMa},${r.voltageV},${r.powerW},${r.temperatureCelsius},${r.isCharging},${r.isScreenOn},${r.packageName}"
                )
            }
        }
    }

    suspend fun exportFpsSession(sessionId: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            val records = fpsSessionDao.getFrameDataBySessionOnce(sessionId)
            // Determine max CPU core count for dynamic columns
            val maxCores = records.maxOfOrNull { r ->
                maxOf(
                    if (r.cpuCoreLoadsJson.isNotEmpty()) r.cpuCoreLoadsJson.split(",").size else 0,
                    if (r.cpuCoreFreqsJson.isNotEmpty()) r.cpuCoreFreqsJson.split(",").size else 0,
                )
            } ?: 0

            // Build header
            val header = buildString {
                append("timestamp,FPS,JANK,BigJANK,Max FrameTime(ms)")
                append(",CPU(%),CPU(℃)")
                for (i in 0 until maxCores) append(",CPU${i}(%)")
                for (i in 0 until maxCores) append(",CPU${i}(MHz)")
                append(",GPU(%),GPU(℃),GPU(MHz)")
                append(",Battery(%),Battery(℃),Current(mA),Power(mW)")
            }
            writer.appendLine(header)

            for (r in records) {
                val coreLoads = if (r.cpuCoreLoadsJson.isNotEmpty()) {
                    r.cpuCoreLoadsJson.split(",").map { it.trim() }
                } else emptyList()
                val coreFreqs = if (r.cpuCoreFreqsJson.isNotEmpty()) {
                    r.cpuCoreFreqsJson.split(",").map { it.trim() }
                } else emptyList()

                val row = buildString {
                    append("${r.timestamp},${r.fps},${r.jankCount},${r.bigJankCount},${r.maxFrameTimeMs}")
                    append(",${r.cpuLoad},${r.cpuTemp}")
                    for (i in 0 until maxCores) append(",${coreLoads.getOrElse(i) { "0" }}")
                    for (i in 0 until maxCores) append(",${coreFreqs.getOrElse(i) { "0" }}")
                    append(",${r.gpuLoad},${r.gpuTemp},${r.gpuFreqMhz}")
                    append(",${r.batteryCapacity},${r.batteryTemp},${r.batteryCurrentMa},${(r.powerW * 1000).toInt()}")
                }
                writer.appendLine(row)
            }
        }
    }
}
