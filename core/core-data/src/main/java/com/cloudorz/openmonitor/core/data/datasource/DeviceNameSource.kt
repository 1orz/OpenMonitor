package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the device marketing name from the bundled devices.db SQLite database.
 *
 * devices.db schema: CREATE TABLE devices (name, device, model)
 * - name:   marketing name, e.g. "OnePlus 13"
 * - device: Build.DEVICE code, e.g. "OP5D0DL1"
 * - model:  Build.MODEL,   e.g. "PJZ110"
 */
@Singleton
class DeviceNameSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DeviceNameSource"
        private const val DB_ASSET = "databases/devices.db"
        private const val DB_FILE = "devices.db"
    }

    @Volatile private var resolved = false
    @Volatile private var cachedName: String? = null

    fun getDeviceName(): String? {
        if (resolved) return cachedName
        cachedName = resolve()
        resolved = true
        return cachedName
    }

    private fun resolve(): String? {
        val db = openDb() ?: return null
        return try {
            val device = Build.DEVICE.trim()
            val model = Build.MODEL.trim()

            // Level 1: device + model exact match
            db.rawQuery(
                "SELECT name FROM devices WHERE device LIKE ? AND model LIKE ? LIMIT 1",
                arrayOf(device, model),
            ).use { c -> if (c.moveToFirst()) return c.getString(0) }

            // Level 2: model only
            db.rawQuery(
                "SELECT name FROM devices WHERE model LIKE ? LIMIT 1",
                arrayOf(model),
            ).use { c -> if (c.moveToFirst()) return c.getString(0) }

            // Level 3: device only
            db.rawQuery(
                "SELECT name FROM devices WHERE device LIKE ? LIMIT 1",
                arrayOf(device),
            ).use { c -> if (c.moveToFirst()) return c.getString(0) }

            null
        } catch (e: Exception) {
            Log.d(TAG, "query failed", e)
            null
        } finally {
            db.close()
        }
    }

    private fun openDb(): SQLiteDatabase? {
        val dbFile = File(context.filesDir, DB_FILE)
        if (!dbFile.exists()) {
            try {
                context.assets.open(DB_ASSET).use { input ->
                    FileOutputStream(dbFile).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.d(TAG, "failed to copy DB from assets", e)
                return null
            }
        }
        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.d(TAG, "failed to open DB", e)
            null
        }
    }
}
