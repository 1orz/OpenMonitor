package com.cloudorz.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.ui.theme.MonitorTheme
import com.cloudorz.monitor.feature.appbias.AppBiasScreen
import com.cloudorz.monitor.feature.charge.ChargeScreen
import com.cloudorz.monitor.feature.cpu.CpuScreen
import com.cloudorz.monitor.feature.floatmonitor.FloatMonitorScreen
import com.cloudorz.monitor.feature.fps.FpsScreen
import com.cloudorz.monitor.feature.overview.OverviewScreen
import com.cloudorz.monitor.feature.power.PowerScreen
import com.cloudorz.monitor.feature.process.ProcessScreen
import com.cloudorz.monitor.ui.features.FeaturesScreen
import com.cloudorz.monitor.ui.navigation.BottomNavBar
import com.cloudorz.monitor.ui.navigation.FeatureRoute
import com.cloudorz.monitor.ui.navigation.Route
import com.cloudorz.monitor.ui.splash.SplashScreen
import com.cloudorz.monitor.ui.user.UserScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonitorTheme {
                MonitorAppContent(permissionManager)
            }
        }
    }
}

@Composable
private fun MonitorAppContent(permissionManager: PermissionManager) {
    // 若已从 SharedPreferences 恢复模式，跳过 SplashScreen
    var selectedMode by rememberSaveable {
        mutableStateOf<PrivilegeMode?>(
            if (permissionManager.hasPersistedMode) permissionManager.currentMode.value else null
        )
    }

    if (selectedMode == null) {
        SplashScreen(
            permissionManager = permissionManager,
            onModeSelected = { mode -> selectedMode = mode },
        )
    } else {
        MainScreen(permissionManager)
    }
}

@Composable
private fun MainScreen(permissionManager: PermissionManager) {
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
            startDestination = Route.Features.route,
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
            composable(Route.Control.route) {
                CpuScreen()
            }
            composable(Route.User.route) {
                UserScreen(permissionManager = permissionManager)
            }

            // ── Feature sub-pages ──
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
            composable(FeatureRoute.APPBIAS) {
                AppBiasScreen()
            }
            composable(FeatureRoute.FLOAT) {
                FloatMonitorScreen()
            }
        }
    }
}
