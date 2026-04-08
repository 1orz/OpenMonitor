package com.cloudorz.openmonitor.feature.keyattestation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.feature.keyattestation.attestation.CertificateInfo
import com.cloudorz.openmonitor.feature.keyattestation.attestation.RootOfTrust
import java.text.DateFormat
import java.util.Date

@Composable
fun KeyAttestationScreen(
    viewModel: KeyAttestationViewModel = hiltViewModel(),
    onProvideTopBarActions: (@Composable () -> Unit) -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val cfg = viewModel.settings.collectAsStateWithLifecycle().value

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pkcs7-certificates")
    ) { uri -> uri?.let { viewModel.saveCerts(it) } }

    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadCerts(it) } }

    // Provide 3-dot menu to MainActivity's TopAppBar
    DisposableEffect(cfg) {
        onProvideTopBarActions {
            AttestationMenuButton(
                viewModel = viewModel,
                cfg = cfg,
                onSave = { saveLauncher.launch("attestation.p7b") },
                onLoad = { loadLauncher.launch(arrayOf("*/*")) },
            )
        }
        onDispose { onProvideTopBarActions {} }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.error != null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ka_attestation_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.ka_retry))
                        }
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Trust banner
        if (!state.isTrusted) {
            item { TrustBanner() }
        }

        // 2. Verified Boot State card
        item { BootStateCard(state) }

        // 3. Attestation info card
        item { AttestationInfoCard(state) }

        // 4. Revocation list row
        item { RevocationListRow(state, onRefresh = { viewModel.refreshRevocationList() }) }

        // 5. Certificate chain
        item { CertChainCard(state) }

        // 6. Authorization list
        if (state.authFields.isNotEmpty()) {
            item { AuthorizationListCard(state) }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ─── Trust Banner ────────────────────────────────────────────────────────────

@Composable
private fun TrustBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.GppBad,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.ka_trust_chain_untrusted),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.ka_trust_chain_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ─── Boot State Card ─────────────────────────────────────────────────────────

@Composable
private fun BootStateCard(state: AttestationUiState) {
    val stateRaw = state.verifiedBootStateRaw
    val isLocked = state.deviceLocked == true
    val containerColor = when (stateRaw) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> if (isLocked)
            Color(0xFF1B5E20).copy(alpha = 0.12f) else Color(0xFFF57F17).copy(alpha = 0.12f)
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> Color(0xFFF57F17).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val bootIcon: ImageVector = when (stateRaw) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> if (isLocked) Icons.Outlined.GppGood else Icons.Outlined.GppMaybe
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> Icons.Outlined.GppMaybe
        else -> Icons.Outlined.GppBad
    }
    val bootColor: Color = when (stateRaw) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> if (isLocked) Color(0xFF2E7D32) else Color(0xFFF9A825)
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> Color(0xFFF9A825)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(bootIcon, null, modifier = Modifier.size(36.dp), tint = bootColor)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.ka_boot_state),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.verifiedBootState ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = bootColor,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isLocked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (isLocked) R.string.ka_bootloader_locked else R.string.ka_bootloader_unlocked
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ─── Attestation Info Card ───────────────────────────────────────────────────

@Composable
private fun AttestationInfoCard(state: AttestationUiState) {
    SectionCard(title = stringResource(R.string.ka_attestation_info), icon = Icons.Outlined.Security) {
        InfoRow(stringResource(R.string.ka_attestation_version), state.attestationVersion)
        InfoRow(stringResource(R.string.ka_attestation_security), state.attestationSecurityLevel)
        InfoRow(stringResource(R.string.ka_keymaster_version), state.keymasterVersion)
        InfoRow(stringResource(R.string.ka_keymaster_security), state.keymasterSecurityLevel)
        if (state.challengeDisplay != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(stringResource(R.string.ka_challenge), state.challengeDisplay, fullWidth = true)
        }
        if (state.uniqueIdDisplay != null) {
            InfoRow(stringResource(R.string.ka_unique_id), state.uniqueIdDisplay, fullWidth = true)
        }
    }
}

// ─── Revocation List Row ─────────────────────────────────────────────────────

@Composable
private fun RevocationListRow(state: AttestationUiState, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ka_revocation_list),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            if (state.revocationSource == "network") {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.revocationEntryCount > 0) {
                val df = DateFormat.getTimeInstance(DateFormat.SHORT)
                Text(
                    text = stringResource(R.string.ka_revocation_entries, state.revocationEntryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.revocationLastFetchMs > 0) {
                    Text(
                        text = "  |  ${df.format(Date(state.revocationLastFetchMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.revocationCacheExpiryMs > 0) {
                    Text(
                        text = "  |  ${df.format(Date(state.revocationCacheExpiryMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.ka_revocation_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.ka_revocation_refresh),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Certificate Chain Card ──────────────────────────────────────────────────

@Composable
private fun CertChainCard(state: AttestationUiState) {
    SectionCard(title = stringResource(R.string.ka_cert_chain), icon = Icons.Outlined.VerifiedUser) {
        state.certChainInfo.forEachIndexed { i, cert ->
            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            CertItem(cert)
        }
    }
}

@Composable
private fun CertItem(cert: CertChainItem) {
    val statusIcon: ImageVector
    val statusColor: Color
    when (cert.statusCode) {
        CertificateInfo.CERT_NORMAL -> {
            statusIcon = Icons.Outlined.CheckCircle
            statusColor = Color(0xFF2E7D32)
        }
        CertificateInfo.CERT_REVOKED -> {
            statusIcon = Icons.Outlined.Cancel
            statusColor = MaterialTheme.colorScheme.error
        }
        CertificateInfo.CERT_EXPIRED -> {
            statusIcon = Icons.Outlined.Info
            statusColor = Color(0xFFF9A825)
        }
        else -> {
            statusIcon = Icons.Outlined.Error
            statusColor = MaterialTheme.colorScheme.error
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            statusIcon,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = statusColor,
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Index + issuer tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ka_cert_index, cert.index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                cert.issuerTag?.let { tag ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            CertDetailRow(stringResource(R.string.ka_subject), cert.subject)
            CertDetailRow(stringResource(R.string.ka_cert_not_before), cert.notBefore)
            CertDetailRow(stringResource(R.string.ka_cert_not_after), cert.notAfter)
            if (cert.statusCode == CertificateInfo.CERT_REVOKED) {
                val revText = buildString {
                    cert.revocationStatus?.let { append(it) }
                    cert.revocationReason?.let { append(" ($it)") }
                }
                if (revText.isNotBlank()) {
                    CertDetailRow(stringResource(R.string.ka_cert_revoked), revText)
                }
            }
        }
    }
}

@Composable
private fun CertDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Authorization List Card ─────────────────────────────────────────────────

@Composable
private fun AuthorizationListCard(state: AttestationUiState) {
    SectionCard(title = stringResource(R.string.ka_authorization_list), icon = Icons.Outlined.Key) {
        state.authFields.forEachIndexed { i, field ->
            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            AuthFieldRow(field)
        }
    }
}

@Composable
private fun AuthFieldRow(field: AuthField) {
    if (field.isFullWidth) {
        // Full-width layout: label + badge on top row, value below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(field.labelResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                HwSwBadge(field.isHardware)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(field.labelResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp),
            )
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            HwSwBadge(field.isHardware)
        }
    }
}

@Composable
private fun HwSwBadge(isHardware: Boolean) {
    val bgColor = if (isHardware)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = if (isHardware)
        MaterialTheme.colorScheme.onTertiaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (isHardware)
        stringResource(R.string.ka_hw_badge)
    else
        stringResource(R.string.ka_sw_badge)

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .background(bgColor, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

// ─── Top-bar 3-dot menu ──────────────────────────────────────────────────────

@Composable
private fun AttestationMenuButton(
    viewModel: KeyAttestationViewModel,
    cfg: AttestationSettings,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.ka_menu_more))
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        // Toggle: Use Attest Key
        if (cfg.hasAttestKey) {
            DropdownMenuItem(
                text = {
                    MenuToggleRow(
                        label = stringResource(R.string.ka_menu_use_attest_key),
                        checked = cfg.useAttestKey,
                    )
                },
                onClick = {
                    expanded = false
                    viewModel.setUseAttestKey(!cfg.useAttestKey)
                },
            )
        }
        // Toggle: Use StrongBox
        if (cfg.hasStrongBox) {
            DropdownMenuItem(
                text = {
                    MenuToggleRow(
                        label = stringResource(R.string.ka_menu_use_strongbox),
                        checked = cfg.useStrongBox,
                    )
                },
                onClick = {
                    expanded = false
                    viewModel.setUseStrongBox(!cfg.useStrongBox)
                },
            )
        }
        // Toggle: Shizuku mode
        if (cfg.isShizukuAvailable) {
            DropdownMenuItem(
                text = {
                    MenuToggleRow(
                        label = stringResource(R.string.ka_menu_use_shizuku),
                        checked = cfg.useShizuku,
                    )
                },
                onClick = {
                    expanded = false
                    viewModel.setUseShizuku(!cfg.useShizuku)
                },
            )
        }

        HorizontalDivider()

        // Reset (delete persistent attest key)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ka_menu_reset)) },
            onClick = {
                expanded = false
                viewModel.reset()
            },
        )

        HorizontalDivider()

        // Save to file
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ka_menu_save)) },
            onClick = {
                expanded = false
                onSave()
            },
        )

        // Load from file
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ka_menu_load)) },
            onClick = {
                expanded = false
                onLoad()
            },
        )
    }
}

@Composable
private fun MenuToggleRow(label: String, checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        if (checked) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            // empty placeholder to keep layout stable
            Spacer(Modifier.size(18.dp))
        }
    }
}

// ─── Shared helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?, fullWidth: Boolean = false) {
    value ?: return
    if (fullWidth) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(130.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
