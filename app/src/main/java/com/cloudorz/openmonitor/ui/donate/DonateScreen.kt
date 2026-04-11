package com.cloudorz.openmonitor.ui.donate

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.elvishew.xlog.XLog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonateScreen(
    viewModel: DonateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Amount selection chips
        Text(
            text = stringResource(R.string.donate_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 4,
        ) {
            DonateViewModel.PRESET_AMOUNTS.forEach { amount ->
                FilterChip(
                    selected = !uiState.isCustom && uiState.selectedAmount == amount,
                    onClick = {
                        view.hapticClick()
                        viewModel.selectAmount(amount)
                    },
                    label = { Text("\u00A5$amount") },
                    modifier = Modifier.weight(1f),
                )
            }
            FilterChip(
                selected = uiState.isCustom,
                onClick = {
                    view.hapticClick()
                    viewModel.selectCustom()
                },
                label = { Text(stringResource(R.string.donate_custom)) },
                modifier = Modifier.weight(1f),
            )
        }

        if (uiState.isCustom) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.customAmount,
                onValueChange = { viewModel.updateCustomAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("\u00A5") },
                placeholder = { Text("0.01 - 999.99") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = uiState.customAmount.isNotEmpty() && !viewModel.isValidAmount(uiState.customAmount),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Donate button
        val canDonate = uiState.state == DonateState.IDLE &&
            viewModel.isValidAmount(viewModel.getEffectiveAmount())

        Button(
            onClick = {
                view.hapticClick()
                viewModel.donate()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canDonate,
        ) {
            Text(stringResource(R.string.donate_button))
        }

        // Turnstile challenge dialog
        if (uiState.state == DonateState.CHALLENGING) {
            TurnstileChallengeDialog(
                challengeUrl = DonateViewModel.CHALLENGE_URL,
                onTokenReceived = { token -> viewModel.onTurnstileToken(token) },
                onFailed = { viewModel.onTurnstileFailed() },
                onDismiss = { viewModel.retry() },
            )
        }

        // Status display
        when (uiState.state) {
            DonateState.CHALLENGING, DonateState.CREATING -> {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (uiState.state == DonateState.CHALLENGING) R.string.donate_verifying
                            else R.string.donate_creating,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DonateState.WAITING_PAYMENT -> {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // QR Code
                    val qrBitmap = remember(uiState.qrCode) {
                        generateQrBitmap(uiState.qrCode, 512)
                    }
                    if (qrBitmap != null) {
                        Text(
                            text = stringResource(R.string.donate_qr_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.donate_qr_title),
                            modifier = Modifier.size(240.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.donate_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            view.hapticClick()
                            DonateViewModel.launchAlipay(context, uiState.qrCode)
                        },
                    ) {
                        Text(stringResource(R.string.donate_open_alipay))
                    }
                }
            }

            DonateState.SUCCESS -> {
                Spacer(modifier = Modifier.height(24.dp))
                val successColor by animateColorAsState(
                    targetValue = Color(0xFF4CAF50),
                    label = "success_color",
                )
                Text(
                    text = stringResource(R.string.donate_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = successColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            DonateState.FAILED -> {
                Spacer(modifier = Modifier.height(24.dp))
                val errorText = when (uiState.error) {
                    DonateError.TIMEOUT -> stringResource(R.string.donate_timeout)
                    DonateError.CHALLENGE_FAILED -> stringResource(R.string.donate_challenge_failed)
                    else -> stringResource(R.string.donate_failed)
                }
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        view.hapticClick()
                        viewModel.retry()
                    },
                ) {
                    Text(stringResource(R.string.donate_retry))
                }
            }

            DonateState.IDLE -> { /* no status to show */ }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileChallengeDialog(
    challengeUrl: String,
    onTokenReceived: (String) -> Unit,
    onFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tokenReceived by remember { mutableStateOf(false) }

    // Timeout: if no token received in 15 seconds, fail
    LaunchedEffect(Unit) {
        delay(15_000L)
        if (!tokenReceived) {
            onFailed()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.donate_verifying),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    XLog.tag("Turnstile").i("page loaded: $url")
                                }
                                override fun onReceivedError(
                                    view: WebView?, request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?,
                                ) {
                                    XLog.tag("Turnstile").w("load error: ${error?.description} (${error?.errorCode})")
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                    XLog.tag("Turnstile").i("JS: ${msg?.message()}")
                                    return true
                                }
                            }
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onToken(token: String) {
                                        XLog.tag("Turnstile").i("token received: ${token.take(16)}...")
                                        tokenReceived = true
                                        post { onTokenReceived(token) }
                                    }
                                },
                                "TurnstileBridge",
                            )
                            XLog.tag("Turnstile").i("loading: $challengeUrl")
                            loadUrl(challengeUrl)
                        }
                    },
                    modifier = Modifier.size(1.dp),
                )
            }
        },
    )
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
