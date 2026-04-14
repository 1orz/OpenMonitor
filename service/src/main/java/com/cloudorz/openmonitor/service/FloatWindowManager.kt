package com.cloudorz.openmonitor.service

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
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

        /** 获取真实屏幕尺寸（包含系统栏区域） */
        private fun getRealScreenSize(wm: WindowManager, res: android.content.res.Resources): Point {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                Point(bounds.width(), bounds.height())
            } else {
                val point = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.getRealSize(point)
                if (point.x == 0 || point.y == 0) {
                    point.x = res.displayMetrics.widthPixels
                    point.y = res.displayMetrics.heightPixels
                }
                point
            }
        }

        /** 获取状态栏高度 */
        private fun getStatusBarHeight(res: android.content.res.Resources): Int {
            val resId = res.getIdentifier("status_bar_height", "dimen", "android")
            return if (resId > 0) res.getDimensionPixelSize(resId) else 0
        }
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
            draggable -> baseFlags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
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
            if ((aboveStatusBar || draggable) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onClick.invoke()
                    }
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

        // 监听系统栏变化（沉浸模式退出、状态栏出现等），自动修正窗口位置
        if (draggable) {
            rootView.setOnApplyWindowInsetsListener { _, insets ->
                ensureWindowsWithinBounds()
                insets
            }
        }

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

    fun updateWindowPositionBounded(id: String, x: Int, y: Int) {
        val window = activeWindows[id] ?: return
        val w = window.view.width.coerceAtLeast(1)
        val h = window.view.height.coerceAtLeast(1)

        val topInset: Int
        val screenW: Int
        val screenH: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenW = metrics.bounds.width()
            screenH = metrics.bounds.height()
            topInset = metrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.statusBars()).top
        } else {
            val res = context.resources
            val realSize = getRealScreenSize(windowManager, res)
            screenW = realSize.x
            screenH = realSize.y
            topInset = getStatusBarHeight(res)
        }

        window.params.x = x.coerceIn(0, (screenW - w).coerceAtLeast(0))
        window.params.y = y.coerceIn(topInset, (screenH - h).coerceAtLeast(topInset))

        try {
            windowManager.updateViewLayout(window.view, window.params)
        } catch (e: Exception) {
            Log.d(TAG, "updateWindowPositionBounded($id) failed", e)
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

    /**
     * 确保所有可拖拽悬浮窗在当前屏幕安全区域内。
     * 当状态栏出现/消失或屏幕旋转时调用，防止窗口被系统栏遮挡。
     */
    fun ensureWindowsWithinBounds() {
        val topInset: Int
        val screenW: Int
        val screenH: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenW = metrics.bounds.width()
            screenH = metrics.bounds.height()
            topInset = metrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.statusBars()).top
        } else {
            val res = context.resources
            val realSize = getRealScreenSize(windowManager, res)
            screenW = realSize.x
            screenH = realSize.y
            topInset = getStatusBarHeight(res)
        }

        activeWindows.values.forEach { window ->
            val v = window.view
            if (v !is DraggableFrameLayout) return@forEach
            val p = window.params
            val w = v.width.coerceAtLeast(1)
            val h = v.height.coerceAtLeast(1)
            val clampedX = p.x.coerceIn(0, (screenW - w).coerceAtLeast(0))
            val clampedY = p.y.coerceIn(topInset, (screenH - h).coerceAtLeast(topInset))
            if (clampedX != p.x || clampedY != p.y) {
                p.x = clampedX
                p.y = clampedY
                try {
                    windowManager.updateViewLayout(v, p)
                } catch (_: Exception) {}
                posPrefs.edit {
                    putInt("${window.id}_x", clampedX)
                    putInt("${window.id}_y", clampedY)
                }
            }
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
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                    val w = width.coerceAtLeast(1)
                    val h = height.coerceAtLeast(1)
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()

                    // 实时检测状态栏是否存在，有则留出高度，无则撑满
                    val wm = windowMgr
                    val topInset: Int
                    val screenW: Int
                    val screenH: Int
                    if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val metrics = wm.currentWindowMetrics
                        screenW = metrics.bounds.width()
                        screenH = metrics.bounds.height()
                        topInset = metrics.windowInsets
                            .getInsets(android.view.WindowInsets.Type.statusBars()).top
                    } else {
                        val realSize = if (wm != null) getRealScreenSize(wm, resources)
                            else Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                        screenW = realSize.x
                        screenH = realSize.y
                        topInset = getStatusBarHeight(resources)
                    }

                    params.x = newX.coerceIn(0, (screenW - w).coerceAtLeast(0))
                    params.y = newY.coerceIn(topInset, (screenH - h).coerceAtLeast(topInset))
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
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onDoubleTap.invoke()
                            lastClickTime = 0L
                        } else {
                            if (onClick != null) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
