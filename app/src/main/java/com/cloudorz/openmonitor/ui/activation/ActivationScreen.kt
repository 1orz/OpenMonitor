package com.cloudorz.openmonitor.ui.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.data.repository.ActivationRepository
import com.cloudorz.openmonitor.core.data.repository.DeviceIdentityRepository
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.hapticClickable
import com.cloudorz.openmonitor.ui.CommunityLinks
import kotlinx.coroutines.launch

@Composable
fun ActivationScreen(
    identityRepository: DeviceIdentityRepository,
    activationRepository: ActivationRepository,
    onActivated: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val deviceUuid = remember { identityRepository.getCachedIdentity()?.uuid ?: "" }
    var activationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val errorInvalidCode = stringResource(R.string.activation_invalid_code)
    val errorNetworkError = stringResource(R.string.activation_network_error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.activation_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.activation_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Device UUID card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.activation_device_id),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = deviceUuid.ifEmpty { "..." },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = {
                    view.hapticClick()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Device UUID", deviceUuid))
                    Toast.makeText(context, context.getString(R.string.activation_copied), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.activation_copied),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Activation code input
        OutlinedTextField(
            value = activationCode,
            onValueChange = {
                activationCode = it.trim()
                errorMessage = null
            },
            label = { Text(stringResource(R.string.activation_code_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(24.dp))

        // Activate button
        Button(
            onClick = {
                view.hapticClick()
                if (activationCode.isBlank() || deviceUuid.isBlank()) return@Button
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    activationRepository.activate(deviceUuid, activationCode).fold(
                        onSuccess = {
                            isLoading = false
                            onActivated()
                        },
                        onFailure = { e ->
                            isLoading = false
                            errorMessage = when {
                                e.message?.contains("not bound") == true -> context.getString(R.string.activation_wrong_device)
                                e.message?.contains("revoked") == true -> context.getString(R.string.activation_revoked)
                                e.message?.contains("404") == true || e.message?.contains("invalid") == true -> errorInvalidCode
                                else -> e.message ?: errorNetworkError
                            }
                        },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = activationCode.isNotBlank() && deviceUuid.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.activation_activating))
            } else {
                Text(stringResource(R.string.activation_activate))
            }
        }

        Spacer(Modifier.height(32.dp))

        // Beta notice
        Text(
            text = stringResource(R.string.activation_contact),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Community links
        val communityShape = RoundedCornerShape(12.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(communityShape),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = communityShape,
        ) {
            Column {
                // Telegram
                ListItem(
                    modifier = Modifier.hapticClickable(onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, CommunityLinks.TELEGRAM_URL.toUri()),
                            )
                        } catch (_: Exception) {}
                    }),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    headlineContent = { Text(stringResource(R.string.join_telegram)) },
                    supportingContent = {
                        Text(
                            text = CommunityLinks.TELEGRAM_URL,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_telegram),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // QQ Group
                ListItem(
                    modifier = Modifier.hapticClickable(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QQ Group", CommunityLinks.QQ_GROUP_NUMBER))
                        Toast.makeText(context, context.getString(R.string.qq_group_copied), Toast.LENGTH_SHORT).show()
                        try {
                            val qqIntent = Intent(Intent.ACTION_VIEW, CommunityLinks.QQ_GROUP_URL.toUri())
                            qqIntent.setPackage("com.tencent.mobileqq")
                            context.startActivity(qqIntent)
                        } catch (_: Exception) {
                            // QQ not installed, number already copied
                        }
                    }),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    headlineContent = { Text(stringResource(R.string.join_qq_group)) },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.qq_group_number, CommunityLinks.QQ_GROUP_NUMBER),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_qq),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}
