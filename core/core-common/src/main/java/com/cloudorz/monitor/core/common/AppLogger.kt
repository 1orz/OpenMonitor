package com.cloudorz.monitor.core.common

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppLogEntry(
    val time: String,
    val level: Char,
    val tag: String,
    val message: String,
)

object AppLogger {
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append('D', tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        append('W', tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        append('I', tag, msg)
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append('E', tag, msg)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: Char, tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _entries.update { list ->
            val base = if (list.size >= MAX_ENTRIES) list.drop(1) else list
            base + AppLogEntry(time, level, tag, msg)
        }
    }
}
