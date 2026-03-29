package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfoResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager = context.packageManager
    private val labelCache = LruCache<String, String>(200)
    private val iconCache = LruCache<String, Bitmap>(100)
    private val NOT_FOUND = "\u0000"
    private val ICON_NOT_FOUND_SENTINEL = "##NO_ICON##"

    fun resolveLabel(packageName: String): String {
        val cached = labelCache.get(packageName)
        if (cached != null) return if (cached == NOT_FOUND) "" else cached
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            labelCache.put(packageName, label)
            label
        } catch (_: PackageManager.NameNotFoundException) {
            labelCache.put(packageName, NOT_FOUND)
            ""
        }
    }

    fun resolveIcon(packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        // Check if we already know this package has no icon
        if (labelCache.get(packageName) == ICON_NOT_FOUND_SENTINEL) return null
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(info)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val size = 48
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bmp
            }
            iconCache.put(packageName, bitmap)
            bitmap
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isInstalled(packageName: String): Boolean {
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
