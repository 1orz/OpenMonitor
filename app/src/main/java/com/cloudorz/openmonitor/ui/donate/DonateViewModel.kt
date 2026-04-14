package com.cloudorz.openmonitor.ui.donate

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.DeviceIdentityRepository
import com.cloudorz.openmonitor.core.data.util.ApiEncryptor
import com.cloudorz.openmonitor.core.data.util.ApiResponseParser
import com.cloudorz.openmonitor.core.data.util.ApiSigner
import com.elvishew.xlog.XLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

enum class DonateState { IDLE, CHALLENGING, CREATING, WAITING_PAYMENT, SUCCESS, FAILED }

enum class DonateError {
    TIMEOUT, CHALLENGE_FAILED, NETWORK_ERROR, CLOSED
}

data class DonateUiState(
    val state: DonateState = DonateState.IDLE,
    val selectedAmount: String = "5",
    val customAmount: String = "",
    val isCustom: Boolean = false,
    val qrCode: String = "",
    val tradeNo: String = "",
    val error: DonateError? = null,
    val qrExpiresAt: Long = 0L,
    val isScanned: Boolean = false,
    val buyerLogonId: String? = null,
)

@HiltViewModel
class DonateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identityRepository: DeviceIdentityRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "Donate"
        private const val API_BASE_URL = "https://om-api.cloudorz.com"
        private const val POLL_INTERVAL_MS = 2_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1_000L // 15 minutes (matches Alipay timeout_express)
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        const val CHALLENGE_URL = "$API_BASE_URL/challenge"
        val PRESET_AMOUNTS = listOf("1", "2", "5", "10", "20", "30", "50")

        fun launchAlipay(context: Context, qrCode: String): Boolean {
            val uri = "alipays://platformapi/startapp?appId=20000067&url=${URLEncoder.encode(qrCode, "UTF-8")}".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                XLog.tag(TAG).i("launchAlipay: started Alipay intent")
                true
            } catch (e: Exception) {
                XLog.tag(TAG).w("launchAlipay failed", e)
                false
            }
        }
    }

    private val _uiState = MutableStateFlow(DonateUiState())
    val uiState: StateFlow<DonateUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun selectAmount(amount: String) {
        _uiState.update { it.copy(selectedAmount = amount, isCustom = false) }
    }

    fun selectCustom() {
        _uiState.update { it.copy(isCustom = true) }
    }

    fun updateCustomAmount(amount: String) {
        _uiState.update { it.copy(customAmount = amount) }
    }

    fun getEffectiveAmount(): String {
        val state = _uiState.value
        return if (state.isCustom) state.customAmount else state.selectedAmount
    }

    fun isValidAmount(amount: String): Boolean {
        val value = amount.toDoubleOrNull() ?: return false
        return value in 0.01..999.99
    }

    fun donate() {
        val amount = getEffectiveAmount()
        if (!isValidAmount(amount)) return

        _uiState.update { it.copy(state = DonateState.CHALLENGING, error = null) }
    }

    fun onTurnstileToken(token: String) {
        XLog.tag(TAG).i("onTurnstileToken: received token (${token.take(16)}...)")
        val amount = getEffectiveAmount()
        _uiState.update { it.copy(state = DonateState.CREATING, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = createOrder(amount, token)
            if (result != null) {
                _uiState.update {
                    it.copy(
                        state = DonateState.WAITING_PAYMENT,
                        qrCode = result.first,
                        tradeNo = result.second,
                        qrExpiresAt = System.currentTimeMillis() + POLL_TIMEOUT_MS,
                        isScanned = false,
                    )
                }
                // Try to launch Alipay
                withContext(Dispatchers.Main) {
                    launchAlipay(context, result.first)
                }
                // Start polling for payment status
                startPolling(result.second)
            } else {
                _uiState.update {
                    it.copy(state = DonateState.FAILED, error = DonateError.NETWORK_ERROR)
                }
            }
        }
    }

    fun onTurnstileFailed() {
        _uiState.update {
            it.copy(state = DonateState.FAILED, error = DonateError.CHALLENGE_FAILED)
        }
    }

    fun onQrExpired() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update {
            it.copy(state = DonateState.FAILED, error = DonateError.TIMEOUT)
        }
    }

    fun retry() {
        _uiState.update { it.copy(state = DonateState.IDLE, error = null, isScanned = false) }
    }

    fun resetState() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update { DonateUiState() }
    }

    private fun createOrder(amount: String, turnstileToken: String): Pair<String, String>? {
        val conn = URL("$API_BASE_URL/api/v1/donate").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val lang = context.resources.configuration.locales[0].language
            val deviceUuid = identityRepository.getCachedIdentity()?.uuid
            val body = JSONObject().apply {
                put("amount", amount)
                put("turnstile_token", turnstileToken)
                put("lang", lang)
                if (deviceUuid != null) put("device_uuid", deviceUuid)
            }
            val encrypted = ApiEncryptor.encrypt(body.toString())

            ApiSigner.sign("POST", "/api/v1/donate", encrypted).applyTo(conn)
            conn.outputStream.bufferedWriter().use { it.write(encrypted) }

            val code = conn.responseCode
            if (code != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                XLog.tag(TAG).w("createOrder failed: HTTP $code, body=$errorBody")
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            XLog.tag(TAG).i("createOrder response: $response")
            val data = ApiResponseParser.unwrapData(response)
            val qrCode = data.optString("qr_code", "")
            val tradeNo = data.optString("trade_no", "")
            if (qrCode.isEmpty() || tradeNo.isEmpty()) {
                XLog.tag(TAG).w("createOrder: missing qr_code or trade_no in response")
                return null
            }
            Pair(qrCode, tradeNo)
        } catch (e: Exception) {
            XLog.tag(TAG).e("createOrder exception", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun startPolling(tradeNo: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(POLL_INTERVAL_MS)

                if (System.currentTimeMillis() - startTime > POLL_TIMEOUT_MS) {
                    _uiState.update {
                        it.copy(state = DonateState.FAILED, error = DonateError.TIMEOUT)
                    }
                    break
                }

                val result = checkOrderStatus(tradeNo) ?: continue
                when (result.status) {
                    "TRADE_SUCCESS", "TRADE_FINISHED" -> {
                        _uiState.update { it.copy(state = DonateState.SUCCESS) }
                        break
                    }
                    "TRADE_CLOSED" -> {
                        _uiState.update {
                            it.copy(state = DonateState.FAILED, error = DonateError.CLOSED)
                        }
                        break
                    }
                    "WAIT_BUYER_PAY" -> {
                        _uiState.update {
                            it.copy(
                                isScanned = true,
                                buyerLogonId = result.buyerLogonId ?: it.buyerLogonId,
                            )
                        }
                    }
                }
            }
        }
    }

    private data class OrderStatusResult(
        val status: String,
        val buyerLogonId: String? = null,
    )

    private fun checkOrderStatus(tradeNo: String): OrderStatusResult? {
        val encodedTradeNo = URLEncoder.encode(tradeNo, "UTF-8")
        val path = "/api/v1/donate/status?trade_no=$encodedTradeNo"
        val conn = URL("$API_BASE_URL$path").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            ApiSigner.sign("GET", path, null).applyTo(conn)

            val code = conn.responseCode
            if (code != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                XLog.tag(TAG).w("checkOrderStatus failed: HTTP $code, body=$errorBody")
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val data = ApiResponseParser.unwrapData(response)
            val status = data.optString("status", "").ifEmpty { return null }
            OrderStatusResult(
                status = status,
                buyerLogonId = data.optString("buyer_logon_id", "").ifEmpty { null },
            )
        } catch (e: Exception) {
            XLog.tag(TAG).e("checkOrderStatus exception", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
