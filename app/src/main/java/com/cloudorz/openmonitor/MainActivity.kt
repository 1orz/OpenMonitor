package com.cloudorz.openmonitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.provider.Settings
import com.cloudorz.openmonitor.BuildConfig
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import com.cloudorz.openmonitor.service.FloatMonitorService
import com.cloudorz.openmonitor.core.ui.theme.MonitorTheme
import com.cloudorz.openmonitor.feature.battery.BatteryScreen
import com.cloudorz.openmonitor.feature.floatmonitor.FloatMonitorScreen
import com.cloudorz.openmonitor.feature.fps.FpsScreen
import com.cloudorz.openmonitor.feature.fps.FpsSessionDetailScreen
import com.cloudorz.openmonitor.feature.overview.OverviewScreen
import com.cloudorz.openmonitor.feature.process.ProcessDetailScreen
import com.cloudorz.openmonitor.feature.process.ProcessScreen
import com.cloudorz.openmonitor.feature.hardware.CpuAnalysisScreen
import com.cloudorz.openmonitor.feature.hardware.HardwareInfoScreen
import com.cloudorz.openmonitor.feature.hardware.OpenGLInfoScreen
import com.cloudorz.openmonitor.feature.hardware.PartitionScreen
import com.cloudorz.openmonitor.feature.hardware.VulkanInfoScreen
import com.cloudorz.openmonitor.feature.keyattestation.KeyAttestationScreen
import com.cloudorz.openmonitor.ui.features.FeaturesScreen
import com.cloudorz.openmonitor.ui.log.LogScreen
import com.cloudorz.openmonitor.ui.network.NetworkScreen
import com.cloudorz.openmonitor.ui.sensor.SensorScreen
import com.cloudorz.openmonitor.ui.navigation.FeatureRoute
import com.cloudorz.openmonitor.ui.navigation.Route
import com.cloudorz.openmonitor.ui.splash.PermissionGuideScreen
import com.cloudorz.openmonitor.ui.splash.PermissionSetupScreen
import com.cloudorz.openmonitor.ui.user.LicenseDetailScreen
import com.cloudorz.openmonitor.ui.user.OpenSourceLicensesScreen
import com.cloudorz.openmonitor.ui.user.UserScreen
import com.cloudorz.openmonitor.ui.user.allLibraries
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cloudorz.openmonitor.core.ui.hapticClick
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var daemonManager: DaemonManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
        setContent {
            var darkModePref by remember { mutableIntStateOf(prefs.getInt("dark_mode", 0)) }
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

private enum class StartupPhase { CHECKING, READY, NEEDS_GUIDE, NEEDS_PERMISSIONS }

@Composable
private fun MonitorAppContent(permissionManager: PermissionManager, daemonManager: DaemonManager) {
    var selectedMode by rememberSaveable {
        mutableStateOf(
            if (permissionManager.hasPersistedMode) permissionManager.currentMode.value else null
        )
    }
    var startupPhase by rememberSaveable { mutableStateOf(
        if (selectedMode != null) StartupPhase.CHECKING else StartupPhase.NEEDS_GUIDE
    ) }
    var startupStepResId by remember { mutableStateOf(R.string.startup_initializing) }

    // Cold start: verify Shizuku + daemon for persisted ROOT/SHIZUKU; 5s hard timeout
    LaunchedEffect(Unit) {
        if (selectedMode == null) return@LaunchedEffect

        startupStepResId = R.string.startup_initializing

        withTimeoutOrNull(5_000L) {
            // Shizuku binder check
            if (selectedMode == PrivilegeMode.SHIZUKU) {
                startupStepResId = R.string.startup_checking_shizuku
                var available = false
                repeat(5) {
                    available = permissionManager.isShizukuAvailableSync()
                    if (!available) delay(400)
                }
                if (!available) {
                    selectedMode = null
                    startupPhase = StartupPhase.NEEDS_GUIDE
                    return@withTimeoutOrNull
                }
            }

            // Daemon check for ROOT/SHIZUKU
            if (selectedMode == PrivilegeMode.ROOT || selectedMode == PrivilegeMode.SHIZUKU) {
                startupStepResId = R.string.startup_deploying_daemon
                val result = daemonManager.ensureRunning()
                if (result == DaemonState.FAILED) {
                    selectedMode = null
                    startupPhase = StartupPhase.NEEDS_GUIDE
                    return@withTimeoutOrNull
                }
                startupStepResId = R.string.startup_connecting_daemon
            }

            // ADB mode: try to connect to manually started daemon, but don't block
            if (selectedMode == PrivilegeMode.ADB) {
                startupStepResId = R.string.startup_connecting_daemon
                daemonManager.ensureRunning()
            }

            startupStepResId = R.string.startup_almost_ready
            startupPhase = StartupPhase.READY
        }

        // If timed out before completing, proceed to READY rather than blocking the user indefinitely
        if (startupPhase == StartupPhase.CHECKING) {
            startupPhase = StartupPhase.READY
        }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(startupStepResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        StartupPhase.NEEDS_GUIDE -> {
            PermissionGuideScreen(
                permissionManager = permissionManager,
                daemonManager = daemonManager,
                onModeSelected = { mode ->
                    selectedMode = mode
                    startupPhase = StartupPhase.NEEDS_PERMISSIONS
                },
            )
        }
        StartupPhase.NEEDS_PERMISSIONS -> {
            PermissionSetupScreen(
                onAllGranted = { startupPhase = StartupPhase.READY },
            )
        }
        StartupPhase.READY -> {
            MainScreen(permissionManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(permissionManager: PermissionManager) {
    val context = LocalContext.current

    // Restore saved float monitors on app start
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
        val savedMonitors = prefs.getStringSet("enabled_monitors", emptySet()) ?: emptySet()
        if (savedMonitors.isEmpty()) return@LaunchedEffect

        if (Settings.canDrawOverlays(context)) {
            context.startForegroundService(FloatMonitorService.startIntent(context))
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pagerState = rememberPagerState(initialPage = 1) { Route.all.size }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    val isTopLevel = currentRoute == null || currentRoute == "tabs"

    // Per-page TopAppBar actions (set by child screens like LogScreen)
    var topBarActions by remember { mutableStateOf<@Composable () -> Unit>({}) }
    LaunchedEffect(currentRoute) { topBarActions = {} }

    // Map routes to display titles
    val subPageTitle = when (currentRoute) {
        FeatureRoute.BATTERY -> stringResource(R.string.nav_battery)
        FeatureRoute.FPS -> stringResource(R.string.nav_fps)
        FeatureRoute.FPS_SESSION_DETAIL -> stringResource(R.string.nav_fps_detail)
        FeatureRoute.PROCESS -> stringResource(R.string.nav_process)
        FeatureRoute.PROCESS_DETAIL -> stringResource(R.string.nav_process_detail)
        FeatureRoute.FLOAT -> stringResource(R.string.nav_float_monitor)
        FeatureRoute.SENSOR -> stringResource(R.string.nav_sensor)
        FeatureRoute.NETWORK -> stringResource(R.string.nav_network)
        FeatureRoute.KEY_ATTESTATION -> stringResource(R.string.nav_key_attestation)
        FeatureRoute.LOG -> stringResource(R.string.nav_debug_log)
        FeatureRoute.HARDWARE -> stringResource(R.string.nav_hardware_info)
        FeatureRoute.CPU_ANALYSIS -> stringResource(R.string.nav_cpu_analysis)
        FeatureRoute.VULKAN_INFO -> "Vulkan"
        FeatureRoute.OPENGL_INFO -> "OpenGL ES"
        FeatureRoute.PARTITIONS -> stringResource(com.cloudorz.openmonitor.R.string.partitions)
        FeatureRoute.LICENSES -> stringResource(R.string.settings_open_source_licenses)
        FeatureRoute.LICENSE_DETAIL -> {
            val index = navBackStackEntry?.arguments?.getString("index")?.toIntOrNull() ?: 0
            allLibraries.getOrNull(index)?.name ?: "License"
        }
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isTopLevel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("OpenMonitor", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(subPageTitle ?: "", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { view.hapticClick(); navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                },
                actions = { topBarActions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    Route.all.forEachIndexed { index, route ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                view.hapticClick()
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = { Icon(route.icon, stringResource(route.labelResId)) },
                            label = { Text(stringResource(route.labelResId)) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "tabs",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { -it } },
            popEnterTransition = { slideInHorizontally { -it } },
            popExitTransition = { slideOutHorizontally { it } },
        ) {
            composable("tabs") {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> FeaturesScreen(
                            onFeatureClick = { route -> navController.navigate(route) },
                        )
                        1 -> OverviewScreen()
                        2 -> UserScreen(
                            permissionManager = permissionManager,
                            onNavigateToLicenses = { navController.navigate(FeatureRoute.LICENSES) },
                        )
                    }
                }
            }

            composable(FeatureRoute.HARDWARE) {
                HardwareInfoScreen(
                    onCpuAnalysisClick = { navController.navigate(FeatureRoute.CPU_ANALYSIS) },
                    onVulkanInfoClick = { navController.navigate(FeatureRoute.VULKAN_INFO) },
                    onOpenGLInfoClick = { navController.navigate(FeatureRoute.OPENGL_INFO) },
                    onPartitionsClick = { navController.navigate(FeatureRoute.PARTITIONS) },
                )
            }
            composable(FeatureRoute.CPU_ANALYSIS) { CpuAnalysisScreen() }
            composable(FeatureRoute.VULKAN_INFO) {
                val entry = navController.previousBackStackEntry
                val vm: com.cloudorz.openmonitor.feature.hardware.HardwareInfoViewModel =
                    if (entry != null) androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(entry) else androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                val gpuInfo = vm.uiState.collectAsState().value.gpuInfo
                VulkanInfoScreen(vulkanInfoJson = gpuInfo.vulkanInfoJson)
            }
            composable(FeatureRoute.OPENGL_INFO) { OpenGLInfoScreen() }
            composable(FeatureRoute.PARTITIONS) { PartitionScreen() }
            composable(FeatureRoute.BATTERY) { BatteryScreen() }
            composable(FeatureRoute.FPS) {
                FpsScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(FeatureRoute.fpsSessionDetail(sessionId))
                    },
                )
            }
            composable(FeatureRoute.FPS_SESSION_DETAIL) {
                FpsSessionDetailScreen(onProvideTopBarActions = { topBarActions = it })
            }
            composable(FeatureRoute.PROCESS) {
                ProcessScreen(
                    onProcessClick = { pid ->
                        navController.navigate(FeatureRoute.processDetail(pid))
                    },
                )
            }
            composable(FeatureRoute.PROCESS_DETAIL) {
                ProcessDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(FeatureRoute.FLOAT) { FloatMonitorScreen() }
            composable(FeatureRoute.SENSOR) { SensorScreen() }
            composable(FeatureRoute.NETWORK) { NetworkScreen() }
            composable(FeatureRoute.KEY_ATTESTATION) {
                KeyAttestationScreen(onProvideTopBarActions = { topBarActions = it })
            }
            composable(FeatureRoute.LOG) {
                LogScreen(onProvideTopBarActions = { topBarActions = it })
            }
            composable(FeatureRoute.LICENSES) {
                OpenSourceLicensesScreen(
                    onLicenseClick = { index ->
                        navController.navigate(FeatureRoute.licenseDetail(index))
                    },
                )
            }
            composable(FeatureRoute.LICENSE_DETAIL) { backStackEntry ->
                val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
                LicenseDetailScreen(libraryIndex = index)
            }
        }
    }
}
