package com.cloudorz.openmonitor.feature.floatmonitor

import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cloudorz.openmonitor.core.ui.R

@Composable
fun FloatMonitorScreen(
    viewModel: FloatMonitorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh permission and enabled state every time screen resumes
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermission()
        }
    }

    // context.getString is required here — stringResource() is not available inside LaunchedEffect
    @SuppressLint("LocalContextGetResourceValueCall")
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { msgRes ->
            snackbarHostState.showSnackbar(context.getString(msgRes))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FloatMonitorScreenContent(
            canShowOverlay = uiState.canShowOverlay,
            enabledMonitors = uiState.enabledMonitors,
            onToggleMonitor = viewModel::onToggleMonitor,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }
}

@Composable
private fun FloatMonitorScreenContent(
    canShowOverlay: Boolean,
    enabledMonitors: Set<FloatMonitorType>,
    onToggleMonitor: (FloatMonitorType, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(
            items = FloatMonitorType.entries.toList(),
            key = { it.name },
        ) { monitorType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(monitorType.displayNameRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = monitorType in enabledMonitors,
                    onCheckedChange = { enabled -> view.hapticClick(); onToggleMonitor(monitorType, enabled) },
                    enabled = canShowOverlay,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
