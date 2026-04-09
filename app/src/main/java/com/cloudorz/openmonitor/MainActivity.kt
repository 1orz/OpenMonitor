package com.cloudorz.openmonitor

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.theme.LocalColorMode
import com.cloudorz.openmonitor.core.ui.theme.LocalEnableBlur
import com.cloudorz.openmonitor.core.ui.theme.LocalEnableFloatingBottomBar
import com.cloudorz.openmonitor.core.ui.theme.LocalUiMode
import com.cloudorz.openmonitor.core.ui.theme.MonitorTheme
import com.cloudorz.openmonitor.feature.battery.BatteryScreen
import com.cloudorz.openmonitor.feature.floatmonitor.FloatMonitorScreen
import com.cloudorz.openmonitor.feature.fps.FpsScreen
import com.cloudorz.openmonitor.feature.fps.FpsSessionDetailScreen
import com.cloudorz.openmonitor.feature.hardware.CpuAnalysisScreen
import com.cloudorz.openmonitor.feature.hardware.HardwareInfoScreen
import com.cloudorz.openmonitor.feature.hardware.OpenGLInfoScreen
import com.cloudorz.openmonitor.feature.hardware.PartitionScreen
import com.cloudorz.openmonitor.feature.hardware.VulkanInfoScreen
import com.cloudorz.openmonitor.feature.keyattestation.KeyAttestationScreen
import com.cloudorz.openmonitor.feature.overview.OverviewScreen
import com.cloudorz.openmonitor.feature.process.ProcessDetailScreen
import com.cloudorz.openmonitor.feature.process.ProcessScreen
import com.cloudorz.openmonitor.service.FloatMonitorService
import com.cloudorz.openmonitor.ui.features.FeaturesScreen
import com.cloudorz.openmonitor.ui.log.LogScreen
import com.cloudorz.openmonitor.ui.navigation.LocalNavigator
import com.cloudorz.openmonitor.ui.navigation.Route
import com.cloudorz.openmonitor.ui.navigation.rememberNavigator
import com.cloudorz.openmonitor.ui.network.NetworkScreen
import com.cloudorz.openmonitor.ui.sensor.SensorScreen
import com.cloudorz.openmonitor.ui.splash.PermissionGuideScreen
import com.cloudorz.openmonitor.ui.splash.PermissionSetupScreen
import com.cloudorz.openmonitor.ui.theme.ColorPaletteScreen
import com.cloudorz.openmonitor.ui.theme.ThemeViewModel
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import com.cloudorz.openmonitor.ui.user.LicenseDetailScreen
import com.cloudorz.openmonitor.ui.user.OpenSourceLicensesScreen
import com.cloudorz.openmonitor.ui.user.UserScreen
import com.cloudorz.openmonitor.ui.user.allLibraries
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        setContent {
            val themeViewModel: ThemeViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()

            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, themeState.pageScale) {
                Density(systemDensity.density * themeState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalDensity provides density,
                LocalColorMode provides themeState.appSettings.colorMode.value,
                LocalEnableBlur provides themeState.enableBlur,
                LocalEnableFloatingBottomBar provides themeState.enableFloatingBottomBar,
                LocalUiMode provides themeState.uiMode,
            ) {
                MonitorTheme(appSettings = themeState.appSettings, uiMode = themeState.uiMode) {
                    MonitorAppContent(permissionManager, daemonManager)
                }
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
    var startupStepResId by remember { mutableIntStateOf(R.string.startup_initializing) }

    LaunchedEffect(Unit) {
        if (selectedMode == null) return@LaunchedEffect
        startupStepResId = R.string.startup_initializing
        withTimeoutOrNull(5_000L) {
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
            if (selectedMode == PrivilegeMode.ADB) {
                startupStepResId = R.string.startup_connecting_daemon
                daemonManager.ensureRunning()
            }
            startupStepResId = R.string.startup_almost_ready
            startupPhase = StartupPhase.READY
        }
        if (startupPhase == StartupPhase.CHECKING) {
            startupPhase = StartupPhase.READY
        }
    }

    val shizukuBinderAlive by permissionManager.shizukuBinderAlive.collectAsStateWithLifecycle()
    LaunchedEffect(shizukuBinderAlive) {
        if (!shizukuBinderAlive && selectedMode == PrivilegeMode.SHIZUKU
            && startupPhase == StartupPhase.READY) {
            permissionManager.setMode(PrivilegeMode.BASIC)
            selectedMode = PrivilegeMode.BASIC
        }
    }

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

// ─── Navigation3 MainScreen ────────────────────────────────────────────────────

@Composable
private fun MainScreen(permissionManager: PermissionManager) {
    val context = LocalContext.current
    val navigator = rememberNavigator(Route.Main)

    // Restore saved float monitors on app start
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
        val savedMonitors = prefs.getStringSet("enabled_monitors", emptySet()) ?: emptySet()
        if (savedMonitors.isEmpty()) return@LaunchedEffect
        if (Settings.canDrawOverlays(context)) {
            context.startForegroundService(FloatMonitorService.startIntent(context))
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = navigator.backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            onBack = { navigator.pop() },
            entryProvider = entryProvider {
                // Main tabs
                entry<Route.Main> { TabsScreen(permissionManager) }

                // Hardware
                entry<Route.Hardware> {
                    ScreenWithTopBar(R.string.nav_hardware_info) { p ->
                        Box(Modifier.padding(p)) {
                            HardwareInfoScreen(
                                onCpuAnalysisClick = { navigator.push(Route.CpuAnalysis) },
                                onVulkanInfoClick = { navigator.push(Route.VulkanInfo) },
                                onOpenGLInfoClick = { navigator.push(Route.OpenGLInfo) },
                                onPartitionsClick = { navigator.push(Route.Partitions) },
                            )
                        }
                    }
                }
                entry<Route.CpuAnalysis> {
                    ScreenWithTopBar(R.string.nav_cpu_analysis) { p -> Box(Modifier.padding(p)) { CpuAnalysisScreen() } }
                }
                entry<Route.VulkanInfo> {
                    ScreenWithTopBar("Vulkan") { p ->
                        Box(Modifier.padding(p)) {
                            val vm: com.cloudorz.openmonitor.feature.hardware.HardwareInfoViewModel =
                                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                            val gpuInfo = vm.uiState.collectAsStateWithLifecycle().value.gpuInfo
                            VulkanInfoScreen(vulkanInfoJson = gpuInfo.vulkanInfoJson)
                        }
                    }
                }
                entry<Route.OpenGLInfo> {
                    ScreenWithTopBar("OpenGL ES") { p -> Box(Modifier.padding(p)) { OpenGLInfoScreen() } }
                }
                entry<Route.Partitions> {
                    ScreenWithTopBar(R.string.partitions) { p -> Box(Modifier.padding(p)) { PartitionScreen() } }
                }

                // Battery
                entry<Route.Battery> {
                    ScreenWithTopBar(R.string.nav_battery) { p -> Box(Modifier.padding(p)) { BatteryScreen() } }
                }

                // FPS
                entry<Route.Fps> {
                    ScreenWithTopBar(R.string.nav_fps) { p ->
                        Box(Modifier.padding(p)) {
                            FpsScreen(onSessionClick = { sessionId -> navigator.push(Route.FpsSessionDetail(sessionId)) })
                        }
                    }
                }
                entry<Route.FpsSessionDetail> { key ->
                    ScreenWithTopBarActions(R.string.nav_fps_detail) { p, setActions ->
                        Box(Modifier.padding(p)) {
                            FpsSessionDetailScreen(sessionId = key.sessionId, onProvideTopBarActions = setActions)
                        }
                    }
                }

                // Process
                entry<Route.Process> {
                    ScreenWithTopBar(R.string.nav_process) { p ->
                        Box(Modifier.padding(p)) {
                            ProcessScreen(onProcessClick = { pid -> navigator.push(Route.ProcessDetail(pid.toString())) })
                        }
                    }
                }
                entry<Route.ProcessDetail> { key ->
                    ScreenWithTopBar(R.string.nav_process_detail) { p ->
                        Box(Modifier.padding(p)) { ProcessDetailScreen(onBack = { navigator.pop() }, pid = key.pid) }
                    }
                }

                // Float monitor
                entry<Route.FloatMonitor> {
                    ScreenWithTopBar(R.string.nav_float_monitor) { p -> Box(Modifier.padding(p)) { FloatMonitorScreen() } }
                }

                // Sensor
                entry<Route.Sensor> {
                    ScreenWithTopBar(R.string.nav_sensor) { p -> Box(Modifier.padding(p)) { SensorScreen() } }
                }

                // Network
                entry<Route.Network> {
                    ScreenWithTopBar(R.string.nav_network) { p -> Box(Modifier.padding(p)) { NetworkScreen() } }
                }

                // Key Attestation
                entry<Route.KeyAttestation> {
                    ScreenWithTopBarActions(R.string.nav_key_attestation) { p, setActions ->
                        Box(Modifier.padding(p)) {
                            KeyAttestationScreen(onProvideTopBarActions = setActions)
                        }
                    }
                }

                // Log
                entry<Route.Log> {
                    ScreenWithTopBarActions(R.string.nav_debug_log) { p, setActions ->
                        Box(Modifier.padding(p)) {
                            LogScreen(onProvideTopBarActions = setActions)
                        }
                    }
                }

                // Theme settings (has its own Scaffold)
                entry<Route.ColorPalette> {
                    ColorPaletteScreen(onBack = { navigator.pop() })
                }

                // Licenses
                entry<Route.Licenses> {
                    ScreenWithTopBar(R.string.settings_open_source_licenses) { p ->
                        Box(Modifier.padding(p)) {
                            OpenSourceLicensesScreen(onLicenseClick = { index -> navigator.push(Route.LicenseDetail(index)) })
                        }
                    }
                }
                entry<Route.LicenseDetail> { key ->
                    val title = allLibraries.getOrNull(key.index)?.name ?: "License"
                    ScreenWithTopBar(title) { p ->
                        Box(Modifier.padding(p)) { LicenseDetailScreen(libraryIndex = key.index) }
                    }
                }
            },
        )
    }
}

// ─── TabsScreen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsScreen(permissionManager: PermissionManager) {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val pagerState = rememberPagerState(initialPage = 1) { Route.tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    val pagerContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            when (page) {
                0 -> FeaturesScreen(
                    onFeatureClick = { route -> navigator.push(route) },
                )
                1 -> OverviewScreen()
                2 -> UserScreen(
                    permissionManager = permissionManager,
                    onNavigateToLicenses = { navigator.push(Route.Licenses) },
                    onNavigateToTheme = { navigator.push(Route.ColorPalette) },
                )
            }
        }
    }

    val m3TopBar: @Composable () -> Unit = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OpenMonitor", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    }

    val m3BottomBar: @Composable () -> Unit = {
        NavigationBar {
            Route.tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        view.hapticClick()
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    icon = { Icon(tab.icon, stringResource(tab.labelResId)) },
                    label = { Text(stringResource(tab.labelResId)) },
                    alwaysShowLabel = true,
                )
            }
        }
    }

    when (uiMode) {
        com.cloudorz.openmonitor.core.ui.theme.UiMode.Material -> {
            Scaffold(topBar = m3TopBar, bottomBar = m3BottomBar) { pagerContent(it) }
        }
        com.cloudorz.openmonitor.core.ui.theme.UiMode.Miuix -> {
            val enableBlur = LocalEnableBlur.current
            val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
            val hazeState = remember { HazeState() }
            val miuixSurface = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
            val hazeStyle = if (enableBlur) {
                HazeStyle(
                    backgroundColor = miuixSurface,
                    tint = HazeTint(miuixSurface.copy(0.8f)),
                )
            } else {
                HazeStyle.Unspecified
            }

            @OptIn(ExperimentalHazeApi::class)
            val blurModifier: Modifier = if (enableBlur) {
                Modifier.hazeEffect(hazeState, hazeStyle) {
                    blurRadius = 20.dp
                    inputScale = HazeInputScale.Fixed(0.35f)
                    noiseFactor = 0f
                }
            } else Modifier

            val miuixTopBar: @Composable () -> Unit = {
                top.yukonga.miuix.kmp.basic.TopAppBar(
                    modifier = blurModifier,
                    color = if (enableBlur) androidx.compose.ui.graphics.Color.Transparent else miuixSurface,
                    title = "OpenMonitor",
                )
            }

            val miuixBottomBar: @Composable () -> Unit = {
                if (enableFloatingBottomBar) {
                    top.yukonga.miuix.kmp.basic.FloatingNavigationBar {
                        Route.tabs.forEachIndexed { index, tab ->
                            top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    view.hapticClick()
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = tab.icon,
                                label = stringResource(tab.labelResId),
                            )
                        }
                    }
                } else {
                    top.yukonga.miuix.kmp.basic.NavigationBar(
                        modifier = blurModifier,
                        color = if (enableBlur) androidx.compose.ui.graphics.Color.Transparent else miuixSurface,
                    ) {
                        Route.tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    view.hapticClick()
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = tab.icon,
                                label = stringResource(tab.labelResId),
                            )
                        }
                    }
                }
            }

            top.yukonga.miuix.kmp.basic.Scaffold(
                topBar = miuixTopBar,
                bottomBar = miuixBottomBar,
            ) { innerPadding ->
                Box(
                    modifier = if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier,
                ) {
                    pagerContent(innerPadding)
                }
            }
        }
    }
}

// ─── Screen wrappers ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenWithTopBar(
    titleResId: Int,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val view = LocalView.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResId)) },
                navigationIcon = {
                    IconButton(onClick = { view.hapticClick(); navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenWithTopBar(
    title: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val view = LocalView.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { view.hapticClick(); navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenWithTopBarActions(
    titleResId: Int,
    content: @Composable (PaddingValues, (@Composable () -> Unit) -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current
    val view = LocalView.current
    var topBarActions by remember { mutableStateOf<@Composable () -> Unit>({}) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResId)) },
                navigationIcon = {
                    IconButton(onClick = { view.hapticClick(); navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = { topBarActions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { paddingValues ->
        content(paddingValues) { actions -> topBarActions = actions }
    }
}
