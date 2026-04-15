package com.cloudorz.openmonitor.ui.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import com.cloudorz.openmonitor.ui.component.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cloudorz.openmonitor.BuildConfig
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.model.identity.ActivationPlan
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.hapticClickable
import com.cloudorz.openmonitor.ui.CommunityLinks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AboutScreen(
    viewModel: UserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val copiedToastMessage = stringResource(R.string.about_copied)

    val identity = remember { viewModel.getCachedIdentity() }
    val activationState = remember { viewModel.getCachedActivationState() }
    val isActivated = activationState?.activated == true

    var showFingerprint by remember { mutableStateOf(false) }
    val fingerprint by viewModel.fingerprint.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // ── App Header ──
        AppIcon(size = 72.dp)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "OpenMonitor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.app_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(28.dp))

        // ── Activation Status Card ──
        ActivationCard(
            isActivated = isActivated,
            plan = activationState?.plan ?: ActivationPlan.NONE,
            activatedAt = activationState?.activatedAt ?: 0L,
            expiresAt = activationState?.expiresAt ?: 0L,
        )

        Spacer(Modifier.height(14.dp))

        // ── Device Identity Card ──
        if (identity != null) {
            DeviceIdentityCard(
                uuid = identity.uuid,
                onCopy = {
                    view.hapticClick()
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("UUID", identity.uuid))
                    Toast.makeText(context, copiedToastMessage, Toast.LENGTH_SHORT).show()
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Build Info Card ──
        BuildInfoCard()

        Spacer(Modifier.height(14.dp))

        // ── Device Fingerprint (expandable) ──
        FingerprintSection(
            expanded = showFingerprint,
            onToggle = {
                view.hapticClick()
                showFingerprint = !showFingerprint
                if (showFingerprint) viewModel.loadFingerprint()
            },
            fingerprint = fingerprint,
            identity = identity,
            onCopyAll = { text ->
                view.hapticClick()
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Fingerprint", text))
                Toast.makeText(context, copiedToastMessage, Toast.LENGTH_SHORT).show()
            },
        )

        Spacer(Modifier.height(24.dp))

        // ── Links ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    view.hapticClick()
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://github.com/1orz/OpenMonitor".toUri()),
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_source_code))
            }

            OutlinedButton(
                onClick = {
                    view.hapticClick()
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, CommunityLinks.TELEGRAM_URL.toUri()),
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_telegram),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Telegram")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Activation Card ─────────────────────────────────────────────────────────

@Composable
private fun ActivationCard(
    isActivated: Boolean,
    plan: ActivationPlan,
    activatedAt: Long,
    expiresAt: Long,
) {
    val statusColor = if (isActivated) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isActivated) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.about_activation_status),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Status badge
                Box(
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (isActivated) stringResource(R.string.about_activated)
                        else stringResource(R.string.about_not_activated),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }
            }

            if (isActivated) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))

                // Plan + dates
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoColumn(
                        label = stringResource(R.string.about_plan),
                        value = planDisplayName(plan),
                        modifier = Modifier.weight(1f),
                    )
                    InfoColumn(
                        label = stringResource(R.string.about_activated_at),
                        value = formatDate(activatedAt),
                        modifier = Modifier.weight(1f),
                    )
                    InfoColumn(
                        label = stringResource(R.string.about_expires_at),
                        value = if (expiresAt == 0L) stringResource(R.string.about_permanent) else formatDate(expiresAt),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Device Identity Card ────────────────────────────────────────────────────

@Composable
private fun DeviceIdentityCard(uuid: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_device_identity),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Build Info Card ─────────────────────────────────────────────────────────

@Composable
private fun BuildInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            BuildInfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(10.dp))
            BuildInfoRow(stringResource(R.string.about_commit), BuildConfig.GIT_COMMIT)
            Spacer(Modifier.height(10.dp))
            BuildInfoRow(stringResource(R.string.about_author), "1orz")
            Spacer(Modifier.height(10.dp))
            BuildInfoRow(stringResource(R.string.about_license), "GPLv3")
        }
    }
}

@Composable
private fun BuildInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Fingerprint Section ─────────────────────────────────────────────────────

@Composable
private fun FingerprintSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    fingerprint: com.cloudorz.openmonitor.core.model.identity.DeviceFingerprint?,
    identity: com.cloudorz.openmonitor.core.model.identity.DeviceIdentity?,
    onCopyAll: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            // Toggle header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable(onClick = onToggle)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (expanded) stringResource(R.string.about_hide_fingerprint)
                    else stringResource(R.string.about_show_fingerprint),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded && fingerprint != null,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                val fp = fingerprint ?: return@AnimatedVisibility
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (identity != null) FpRow("UUID", identity.uuid)
                        FpRow("MediaDrm ID", fp.mediaDrmId ?: "N/A")
                        FpRow("Serial No", fp.serialNo ?: "N/A")
                        FpRow("Primary ID", fp.primaryId())
                        FpRow("HW Hash", fp.hardwareHash())
                        FpRow("Model", fp.model)
                        FpRow("Board", fp.board)
                        FpRow("Hardware", fp.hardware)
                        FpRow("Manufacturer", fp.manufacturer)
                        FpRow("Brand", fp.brand)
                        FpRow("Device", fp.device)
                        FpRow("Product", fp.product)
                        FpRow("SoC", "${fp.socManufacturer} ${fp.socModel}".trim())
                        FpRow("Screen", "${fp.screenWidth}x${fp.screenHeight} @${fp.screenDensity}dpi")
                        FpRow("RAM", "${fp.totalRam / 1024 / 1024}MB")
                        FpRow("SDK", "${fp.sdkInt}")
                        FpRow("Mode", fp.privilegeMode)
                        FpRow("Sensor Hash", fp.sensorHash.take(16) + "...")
                    }

                    Spacer(Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = {
                            val text = buildString {
                                if (identity != null) appendLine("UUID: ${identity.uuid}")
                                appendLine("MediaDrm ID: ${fp.mediaDrmId ?: "N/A"}")
                                appendLine("Serial No: ${fp.serialNo ?: "N/A"}")
                                appendLine("Primary ID: ${fp.primaryId()}")
                                appendLine("HW Hash: ${fp.hardwareHash()}")
                                appendLine("Model: ${fp.model}")
                                appendLine("Board: ${fp.board}")
                                appendLine("Hardware: ${fp.hardware}")
                                appendLine("Manufacturer: ${fp.manufacturer}")
                                appendLine("Brand: ${fp.brand}")
                                appendLine("Device: ${fp.device}")
                                appendLine("Product: ${fp.product}")
                                appendLine("SoC: ${fp.socManufacturer} ${fp.socModel}".trim())
                                appendLine("Screen: ${fp.screenWidth}x${fp.screenHeight} @${fp.screenDensity}dpi")
                                appendLine("RAM: ${fp.totalRam / 1024 / 1024}MB")
                                appendLine("SDK: ${fp.sdkInt}")
                                appendLine("Mode: ${fp.privilegeMode}")
                                append("Sensor Hash: ${fp.sensorHash}")
                            }
                            onCopyAll(text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.about_copy_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun FpRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun planDisplayName(plan: ActivationPlan): String = when (plan) {
    ActivationPlan.NONE -> "-"
    ActivationPlan.BASIC -> stringResource(R.string.activation_plan_basic)
    ActivationPlan.PRO -> stringResource(R.string.activation_plan_pro)
    ActivationPlan.ULTIMATE -> stringResource(R.string.activation_plan_ultimate)
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))
}
