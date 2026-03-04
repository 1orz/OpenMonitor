package com.cloudorz.monitor.core.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Creates a polling Flow that emits data at the specified interval.
 * Supports adaptive interval based on data change rate.
 */
fun <T> pollingFlow(
    intervalMs: Long = 1000L,
    emitter: suspend () -> T
): Flow<T> = flow {
    while (currentCoroutineContext().isActive) {
        emit(emitter())
        delay(intervalMs)
    }
}

/**
 * Creates a polling Flow with adaptive interval.
 * When data changes rapidly, the interval decreases. When stable, it increases.
 */
fun <T> adaptivePollingFlow(
    minIntervalMs: Long = 500L,
    maxIntervalMs: Long = 3000L,
    defaultIntervalMs: Long = 1000L,
    emitter: suspend () -> T,
    hasChanged: (old: T, new: T) -> Boolean = { a, b -> a != b }
): Flow<T> = flow {
    var currentInterval = defaultIntervalMs
    var lastValue: T? = null

    while (currentCoroutineContext().isActive) {
        val value = emitter()
        emit(value)

        currentInterval = if (lastValue != null && hasChanged(lastValue!!, value)) {
            (currentInterval * 0.8).toLong().coerceAtLeast(minIntervalMs)
        } else {
            (currentInterval * 1.2).toLong().coerceAtMost(maxIntervalMs)
        }

        lastValue = value
        delay(currentInterval)
    }
}
