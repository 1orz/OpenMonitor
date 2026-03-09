package com.cloudorz.openmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.cloudorz.openmonitor.service.AccessibilityMonitorService
import com.cloudorz.openmonitor.service.FloatMonitorService
import com.cloudorz.openmonitor.core.ui.theme.MonitorTheme
import com.cloudorz.openmonitor.feature.charge.ChargeScreen
import com.cloudorz.openmonitor.feature.cpu.CpuScreen
import com.cloudorz.openmonitor.feature.floatmonitor.FloatMonitorScreen
import com.cloudorz.openmonitor.feature.fps.FpsScreen
import com.cloudorz.openmonitor.feature.overview.OverviewScreen
import com.cloudorz.openmonitor.feature.power.PowerScreen
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
        setContent {
            MonitorTheme {
                MonitorAppContent(permissionManager, daemonManager)
            }
        }
    }
}

private enum class StartupPhase { CHECKING, READY, NEEDS_GUIDE }

@Composable
private fun MonitorAppContent(permissionManager: PermissionManager, daemonManager: DaemonManager) {
    var selectedMode by rememberSaveable {
        mutableStateOf<PrivilegeMode?>(
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

        startupPhase = StartupPhase.READY
    }

    // Runtime: binder died while using SHIZUKU mode
    val shizukuBinderAlive by permissionManager.shizukuBinderAlive.collectAsStateWithLifecycle()
    LaunchedEffect(shizukuBinderAlive) {
        if (!shizukuBinderAlive && selectedMode == PrivilegeMode.SHIZUKU) {
            selectedMode = null
            startupPhase = StartupPhase.NEEDS_GUIDE
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

    // Restore saved float monitors on app start
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("monitor_settings", android.content.Context.MODE_PRIVATE)
        val savedMonitors = prefs.getStringSet("enabled_monitors", emptySet()) ?: emptySet()
        if (savedMonitors.isEmpty()) return@LaunchedEffect

        var canShow = Settings.canDrawOverlays(context) || AccessibilityMonitorService.isEnabled(context)
        if (!canShow && permissionManager.currentMode.value != PrivilegeMode.BASIC) {
            AccessibilityMonitorService.enableViaShell(context, permissionManager.getExecutor())
            delay(1500)
            canShow = AccessibilityMonitorService.isEnabled(context)
        }
        if (canShow) {
            // Service auto-restores saved monitors in startFloatService()
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
            composable(FeatureRoute.POWER) {
                PowerScreen()
            }
            composable(FeatureRoute.CHARGE) {
                ChargeScreen()
            }
            composable(FeatureRoute.FPS) {
                FpsScreen()
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
