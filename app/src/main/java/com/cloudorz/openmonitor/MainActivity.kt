package com.cloudorz.openmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.provider.Settings
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import com.cloudorz.openmonitor.service.FloatMonitorService
import com.cloudorz.openmonitor.core.ui.theme.MonitorTheme
import com.cloudorz.openmonitor.feature.battery.BatteryScreen
import com.cloudorz.openmonitor.feature.cpu.CpuScreen
import com.cloudorz.openmonitor.feature.floatmonitor.FloatMonitorScreen
import com.cloudorz.openmonitor.feature.fps.FpsScreen
import com.cloudorz.openmonitor.feature.fps.FpsSessionDetailScreen
import com.cloudorz.openmonitor.feature.overview.OverviewScreen
import com.cloudorz.openmonitor.feature.process.ProcessScreen
import com.cloudorz.openmonitor.ui.features.FeaturesScreen
import com.cloudorz.openmonitor.ui.log.LogScreen
import com.cloudorz.openmonitor.ui.network.NetworkScreen
import com.cloudorz.openmonitor.ui.sensor.SensorScreen
import com.cloudorz.openmonitor.ui.storage.StorageScreen
import com.cloudorz.openmonitor.ui.navigation.BottomNavBar
import com.cloudorz.openmonitor.ui.navigation.FeatureRoute
import com.cloudorz.openmonitor.ui.navigation.Route
import com.cloudorz.openmonitor.ui.splash.PermissionGuideScreen
import com.cloudorz.openmonitor.ui.user.UserScreen  // 设置页复用
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var daemonManager: DaemonManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("monitor_settings", android.content.Context.MODE_PRIVATE)
        setContent {
            var darkModePref by remember { mutableStateOf(prefs.getInt("dark_mode", 0)) }
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") darkModePref = prefs.getInt("dark_mode", 0)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (darkModePref) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            MonitorTheme(darkTheme = darkTheme) {
                MonitorAppContent(permissionManager, daemonManager)
            }
        }
    }
}

private enum class StartupPhase { CHECKING, READY, NEEDS_GUIDE }

@Composable
private fun MonitorAppContent(permissionManager: PermissionManager, daemonManager: DaemonManager) {
    var selectedMode by rememberSaveable {
        mutableStateOf(
            if (permissionManager.hasPersistedMode) permissionManager.currentMode.value else null
        )
    }
    var startupPhase by remember { mutableStateOf(
        if (selectedMode != null) StartupPhase.CHECKING else StartupPhase.NEEDS_GUIDE
    ) }

    // Cold start: verify Shizuku + daemon for persisted ROOT/SHIZUKU
    LaunchedEffect(Unit) {
        if (selectedMode == null) return@LaunchedEffect

        // Shizuku binder check
        if (selectedMode == PrivilegeMode.SHIZUKU) {
            var available = false
            repeat(5) {
                available = permissionManager.isShizukuAvailableSync()
                if (!available) delay(400)
            }
            if (!available) {
                selectedMode = null
                startupPhase = StartupPhase.NEEDS_GUIDE
                return@LaunchedEffect
            }
        }

        // Daemon check for ROOT/SHIZUKU
        if (selectedMode == PrivilegeMode.ROOT || selectedMode == PrivilegeMode.SHIZUKU) {
            val result = daemonManager.ensureRunning()
            if (result == DaemonState.FAILED) {
                selectedMode = null
                startupPhase = StartupPhase.NEEDS_GUIDE
                return@LaunchedEffect
            }
        }

        // ADB mode: try to connect to manually started daemon, but don't block
        if (selectedMode == PrivilegeMode.ADB) {
            daemonManager.ensureRunning()
        }

        startupPhase = StartupPhase.READY
    }

    // Runtime: Shizuku binder died — keep current screen, sync mode to BASIC
    val shizukuBinderAlive by permissionManager.shizukuBinderAlive.collectAsStateWithLifecycle()
    LaunchedEffect(shizukuBinderAlive) {
        if (!shizukuBinderAlive && selectedMode == PrivilegeMode.SHIZUKU
            && startupPhase == StartupPhase.READY) {
            // Don't force back to guide page — degrade gracefully
            permissionManager.setMode(PrivilegeMode.BASIC)
            selectedMode = PrivilegeMode.BASIC
        }
    }

    // Keep selectedMode in sync with PermissionManager (for Settings page mode switch)
    val currentModeFromManager by permissionManager.currentMode.collectAsStateWithLifecycle()
    LaunchedEffect(currentModeFromManager) {
        if (startupPhase == StartupPhase.READY) {
            selectedMode = currentModeFromManager
        }
    }

    when (startupPhase) {
        StartupPhase.CHECKING -> {
            // Lightweight splash while verifying daemon
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        StartupPhase.NEEDS_GUIDE -> {
            PermissionGuideScreen(
                permissionManager = permissionManager,
                daemonManager = daemonManager,
                onModeSelected = { mode ->
                    selectedMode = mode
                    startupPhase = StartupPhase.READY
                },
            )
        }
        StartupPhase.READY -> {
            MainScreen(permissionManager)
        }
    }
}

@Composable
private fun MainScreen(permissionManager: PermissionManager) {
    val context = LocalContext.current

    // 请求通知权限（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Restore saved float monitors on app start
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("monitor_settings", android.content.Context.MODE_PRIVATE)
        val savedMonitors = prefs.getStringSet("enabled_monitors", emptySet()) ?: emptySet()
        if (savedMonitors.isEmpty()) return@LaunchedEffect

        if (Settings.canDrawOverlays(context)) {
            context.startForegroundService(FloatMonitorService.startIntent(context))
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on top-level tabs
    val topLevelRoutes = Route.all.map { it.route }.toSet()
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Top-level tabs ──
            composable(Route.Features.route) {
                FeaturesScreen(
                    onFeatureClick = { route ->
                        navController.navigate(route)
                    },
                )
            }
            composable(Route.Overview.route) {
                OverviewScreen()
            }
            composable(Route.Settings.route) {
                UserScreen(permissionManager = permissionManager)
            }

            // ── Feature sub-pages ──
            composable(FeatureRoute.CPU) {
                CpuScreen()
            }
            composable(FeatureRoute.BATTERY) {
                BatteryScreen()
            }
            composable(FeatureRoute.FPS) {
                FpsScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(FeatureRoute.fpsSessionDetail(sessionId))
                    },
                )
            }
            composable(FeatureRoute.FPS_SESSION_DETAIL) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                FpsSessionDetailScreen(
                    sessionId = sessionId.toLongOrNull() ?: return@composable,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(FeatureRoute.PROCESS) {
                ProcessScreen()
            }
            composable(FeatureRoute.FLOAT) {
                FloatMonitorScreen()
            }
            composable(FeatureRoute.STORAGE) {
                StorageScreen()
            }
            composable(FeatureRoute.SENSOR) {
                SensorScreen()
            }
            composable(FeatureRoute.NETWORK) {
                NetworkScreen()
            }
            composable(FeatureRoute.LOG) {
                LogScreen()
            }
        }
    }
}
