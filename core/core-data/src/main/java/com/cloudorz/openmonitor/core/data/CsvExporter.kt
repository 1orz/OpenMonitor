package com.cloudorz.openmonitor.core.data

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
) {
    suspend fun exportPowerSession(sessionId: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,capacity,isCharging,ioBytes,packageName,isScreenOn,isFuzzy")
            val records = powerStatDao.getRecordsBySessionOnce(sessionId)
            for (r in records) {
                writer.appendLine(
                    "${r.startTime},${r.capacity},${r.isCharging},${r.ioBytes},${r.packageName},${r.isScreenOn},${r.isFuzzy}"
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

    suspend fun exportFpsSession(sessionId: Long, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.appendLine("timestamp,fps,jankCount,bigJankCount,maxFrameTimeMs")
            val records = fpsSessionDao.getFrameDataBySessionOnce(sessionId)
            for (r in records) {
                writer.appendLine(
                    "${r.timestamp},${r.fps},${r.jankCount},${r.bigJankCount},${r.maxFrameTimeMs}"
                )
            }
        }
    }
}
