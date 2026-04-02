package com.cloudorz.openmonitor.service

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.StateFlow

class FloatWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "FloatWindowManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeWindows = mutableMapOf<String, FloatWindow>()
    private val posPrefs = context.applicationContext.getSharedPreferences("float_window_pos", Context.MODE_PRIVATE)

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
        centerVertical: Boolean = false,
        draggable: Boolean = true,
        aboveStatusBar: Boolean = false,
        onInteraction: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        onDoubleTap: (() -> Unit)? = null,
        darkTheme: StateFlow<Boolean>? = null,
        content: @Composable () -> Unit,
    ) {
        if (activeWindows.containsKey(id)) {
            removeWindow(id)
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val flags = when {
            aboveStatusBar -> baseFlags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            draggable -> baseFlags
            onClick != null -> baseFlags // touchable but non-draggable (control panel)
            else -> baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val savedX = posPrefs.getInt("${id}_x", Int.MIN_VALUE)
        val savedY = posPrefs.getInt("${id}_y", Int.MIN_VALUE)
        val hasSaved = draggable && savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE

        val params = WindowManager.LayoutParams(
            width,
            height,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when {
                !hasSaved && centerHorizontal && centerVertical -> Gravity.CENTER
                !hasSaved && centerHorizontal -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                !hasSaved && centerVertical -> Gravity.CENTER_VERTICAL or Gravity.START
                else -> Gravity.TOP or Gravity.START
            }
            this.x = if (hasSaved) savedX else if (centerHorizontal || centerVertical) 0 else x
            this.y = if (hasSaved) savedY else if (centerVertical) 0 else y
            if (aboveStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val lifecycleOwner = FloatWindowLifecycleOwner()

        val composeView = ComposeView(context).apply {
            setContent {
                if (darkTheme != null) {
                    val isDark by darkTheme.collectAsState()
                    val baseConfig = LocalConfiguration.current
                    val nightConfig = remember(isDark) {
                        Configuration(baseConfig).also { cfg ->
                            cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        }
                    }
                    CompositionLocalProvider(LocalConfiguration provides nightConfig) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }

        // Wrap in DraggableFrameLayout for proper drag + Compose click coexistence
        val rootView: View = when {
            draggable -> {
                DraggableFrameLayout(context, onInteraction, onClick, onLongClick, onDoubleTap, onDragEnd = { px, py ->
                    posPrefs.edit {
                        putInt("${id}_x", px)
                        putInt("${id}_y", py)
                    }
                }).apply {
                    windowParams = params
                    this.windowMgr = this@FloatWindowManager.windowManager
                    addView(composeView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ))
                }
            }
            onClick != null -> {
                // Touchable but non-draggable (e.g. control panel backdrop)
                FrameLayout(context).apply {
                    setOnClickListener { onClick.invoke() }
                    addView(composeView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ))
                }
            }
            else -> composeView
        }

        // Set lifecycle on root view so Compose's WindowRecomposer can find it
        rootView.setViewTreeLifecycleOwner(lifecycleOwner)
        rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(rootView, params)
        activeWindows[id] = FloatWindow(id, rootView, params)

        // Fade-in animation
        run {
            params.alpha = 0f
            try { windowManager.updateViewLayout(rootView, params) } catch (_: Exception) {}
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    params.alpha = anim.animatedValue as Float
                    try { windowManager.updateViewLayout(rootView, params) } catch (_: Exception) {}
                }
                start()
            }
        }
    }

    fun removeWindow(id: String, immediate: Boolean = false) {
        val window = activeWindows.remove(id) ?: return
        if (immediate) {
            try { windowManager.removeView(window.view) } catch (e: Exception) {
                Log.d(TAG, "removeWindow($id) failed", e)
            }
            return
        }
        // Fade-out animation before removing
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 160
            interpolator = AccelerateInterpolator()
            addUpdateListener { anim ->
                window.params.alpha = anim.animatedValue as Float
                try { windowManager.updateViewLayout(window.view, window.params) } catch (_: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    try { windowManager.removeView(window.view) } catch (_: Exception) {}
                }
            })
            start()
        }
    }

    fun removeAllWindows() {
        activeWindows.keys.toList().forEach { removeWindow(it, immediate = true) }
    }

    fun isWindowActive(id: String): Boolean = activeWindows.containsKey(id)

    fun getActiveWindowIds(): Set<String> = activeWindows.keys.toSet()

    fun updateWindowPosition(id: String, x: Int, y: Int) {
        val window = activeWindows[id] ?: return
        window.params.x = x
        window.params.y = y
        try {
            windowManager.updateViewLayout(window.view, window.params)
        } catch (e: Exception) {
            Log.d(TAG, "updateWindowPosition($id) failed", e)
        }
    }

    fun getWindowPosition(id: String): Pair<Int, Int>? {
        val window = activeWindows[id] ?: return null
        return window.params.x to window.params.y
    }

    fun saveWindowPosition(id: String) {
        val window = activeWindows[id] ?: return
        posPrefs.edit {
            putInt("${id}_x", window.params.x)
            putInt("${id}_y", window.params.y)
        }
    }

    fun refreshWindowLayout(id: String) {
        val window = activeWindows[id] ?: return
        try {
            window.view.requestLayout()
            windowManager.updateViewLayout(window.view, window.params)
        } catch (e: Exception) {
            Log.d(TAG, "refreshWindowLayout($id) failed", e)
        }
    }

    fun setWindowLocked(id: String, locked: Boolean) {
        val window = activeWindows[id] ?: return
        val rootView = window.view
        if (rootView is DraggableFrameLayout) {
            rootView.isLocked = locked
        }
    }

    private class DraggableFrameLayout(
        context: Context,
        private val onInteraction: ((Boolean) -> Unit)? = null,
        private val onClick: (() -> Unit)? = null,
        private val onLongClick: (() -> Unit)? = null,
        private val onDoubleTap: (() -> Unit)? = null,
        private val onDragEnd: ((Int, Int) -> Unit)? = null,
    ) : FrameLayout(context) {
        var windowParams: WindowManager.LayoutParams? = null
        var windowMgr: WindowManager? = null
        var isLocked: Boolean = false

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        private var isTouching = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        // Long-press detection
        private val handler = Handler(Looper.getMainLooper())
        private var longPressTriggered = false
        private val longPressRunnable = Runnable {
            if (!isDragging) {
                longPressTriggered = true
                onLongClick?.invoke()
            }
        }

        // Double-tap detection
        private var lastClickTime = 0L
        private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        override fun requestLayout() {
            // During drag/touch, suppress child-triggered layout requests
            // to prevent Compose recomposition from calling updateViewLayout
            // which conflicts with drag position updates.
            if (isTouching) return
            super.requestLayout()
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0
                    initialY = windowParams?.y ?: 0
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                    longPressTriggered = false
                    isTouching = true
                    onInteraction?.invoke(true)
                    if (onLongClick != null) {
                        handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLocked) return false // Don't intercept drag when locked
                    val dx = ev.rawX - initialTouchX
                    val dy = ev.rawY - initialTouchY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                        return true // Steal the gesture for drag
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            return false
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            isDragging = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                    }
                    val params = windowParams ?: return true
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val w = width.coerceAtLeast(1)
                    val h = height.coerceAtLeast(1)

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
                    handler.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        val p = windowParams
                        if (p != null) onDragEnd?.invoke(p.x, p.y)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && !longPressTriggered) {
                        val now = System.currentTimeMillis()
                        if (onDoubleTap != null && now - lastClickTime < doubleTapTimeout) {
                            onDoubleTap.invoke()
                            lastClickTime = 0L
                        } else {
                            onClick?.invoke()
                            performClick()
                            lastClickTime = now
                        }
                    }
                    isDragging = false
                    longPressTriggered = false
                    isTouching = false
                    onInteraction?.invoke(false)
                    // Flush any suppressed layout requests
                    super.requestLayout()
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
