package com.cloudorz.monitor.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "FloatWindowManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeWindows = mutableMapOf<String, FloatWindow>()
    private val useAccessibilityOverlay = context is AccessibilityService

    data class FloatWindow(
        val id: String,
        val view: View,
        val params: WindowManager.LayoutParams,
    )

    fun addWindow(
        id: String,
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        x: Int = 0,
        y: Int = 100,
        centerHorizontal: Boolean = false,
        draggable: Boolean = true,
        aboveStatusBar: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (activeWindows.containsKey(id)) {
            removeWindow(id)
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val flags = if (aboveStatusBar) {
            baseFlags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (draggable) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val windowType = if (useAccessibilityOverlay) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (centerHorizontal) {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.TOP or Gravity.START
            }
            this.x = x
            this.y = y
            if (aboveStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val lifecycleOwner = FloatWindowLifecycleOwner()

        val composeView = ComposeView(context).apply {
            setContent(content)
        }

        // Wrap in DraggableFrameLayout for proper drag + Compose click coexistence
        val rootView: View = if (draggable) {
            DraggableFrameLayout(context).apply {
                windowParams = params
                this.windowMgr = this@FloatWindowManager.windowManager
                addView(composeView)
            }
        } else {
            composeView
        }

        // Set lifecycle on root view so Compose's WindowRecomposer can find it
        rootView.setViewTreeLifecycleOwner(lifecycleOwner)
        rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(rootView, params)

        activeWindows[id] = FloatWindow(id, rootView, params)
    }

    fun removeWindow(id: String) {
        activeWindows.remove(id)?.let { window ->
            try {
                windowManager.removeView(window.view)
            } catch (e: Exception) {
                Log.d(TAG, "removeWindow($id) failed", e)
            }
        }
    }

    fun removeAllWindows() {
        activeWindows.keys.toList().forEach { removeWindow(it) }
    }

    fun isWindowActive(id: String): Boolean = activeWindows.containsKey(id)

    fun getActiveWindowIds(): Set<String> = activeWindows.keys.toSet()

    private class DraggableFrameLayout(context: Context) : FrameLayout(context) {
        var windowParams: WindowManager.LayoutParams? = null
        var windowMgr: WindowManager? = null

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0
                    initialY = windowParams?.y ?: 0
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initialTouchX
                    val dy = ev.rawY - initialTouchY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        isDragging = true
                        return true // Steal the gesture for drag
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val params = windowParams ?: return true
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val w = width.coerceAtLeast(1)
                    val h = height.coerceAtLeast(1)

                    // Clamp to screen bounds in real-time
                    params.x = (initialX + (event.rawX - initialTouchX).toInt())
                        .coerceIn(0, (screenWidth - w).coerceAtLeast(0))
                    params.y = (initialY + (event.rawY - initialTouchY).toInt())
                        .coerceIn(0, (screenHeight - h).coerceAtLeast(0))
                    try {
                        windowMgr?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        Log.d(TAG, "DraggableFrameLayout: updateViewLayout failed", e)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            return true
        }
    }

    private class FloatWindowLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        init {
            savedStateRegistryController.performRestore(null)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }
}
