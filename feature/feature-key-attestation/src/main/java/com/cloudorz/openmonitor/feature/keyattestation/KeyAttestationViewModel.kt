package com.cloudorz.openmonitor.feature.keyattestation

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.feature.keyattestation.attestation.Attestation
import com.cloudorz.openmonitor.feature.keyattestation.attestation.AuthorizationList
import com.cloudorz.openmonitor.feature.keyattestation.attestation.CertificateInfo
import com.cloudorz.openmonitor.feature.keyattestation.attestation.RevocationList
import com.cloudorz.openmonitor.feature.keyattestation.attestation.RootOfTrust
import com.cloudorz.openmonitor.feature.keyattestation.attestation.RootPublicKey
import com.cloudorz.openmonitor.feature.keyattestation.keystore.IKeyAttestKeyStore
import com.cloudorz.openmonitor.feature.keyattestation.keystore.ShizukuKeyStoreManager
import com.google.common.io.BaseEncoding
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.security.auth.x500.X500Principal

/** A single field in the authorization list, tagged with HW or SW. */
data class AuthField(
    val labelResId: Int,
    val value: String,
    val isHardware: Boolean,
    val isFullWidth: Boolean = false,
)

/** Info for one certificate in the chain. */
data class CertChainItem(
    val index: Int,
    val subject: String,
    val notBefore: String,
    val notAfter: String,
    val statusCode: Int,                // CertificateInfo.CERT_* constants
    val revocationStatus: String? = null,
    val revocationReason: String? = null,
    val issuerTag: String? = null,      // "GOOGLE", "AOSP", etc. — only on root cert
)

data class AttestationUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Trust chain validity
    val isTrusted: Boolean = true,

    // Verified boot state (top card)
    val verifiedBootState: String? = null,
    val verifiedBootStateRaw: Int = -1,
    val deviceLocked: Boolean? = null,

    // Attestation / KM version + security
    val attestationVersion: String? = null,
    val attestationSecurityLevel: String? = null,
    val keymasterVersion: String? = null,
    val keymasterSecurityLevel: String? = null,
    val challengeDisplay: String? = null,
    val uniqueIdDisplay: String? = null,

    // Revocation list info
    val revocationEntryCount: Int = 0,
    val revocationLastFetchMs: Long = 0,
    val revocationCacheExpiryMs: Long = 0,

    // Certificate chain
    val rootCertIssuer: String? = null,
    val certChainInfo: List<CertChainItem> = emptyList(),

    // Authorization list fields (HW first, then SW)
    val authFields: List<AuthField> = emptyList(),

    val hasStrongBox: Boolean = false,
)

/** Capabilities and toggle state exposed to the top-bar menu. */
data class AttestationSettings(
    val hasAttestKey: Boolean = false,
    val hasStrongBox: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val useAttestKey: Boolean = true,
    val useStrongBox: Boolean = false,
    val useShizuku: Boolean = false,
)

@HiltViewModel
class KeyAttestationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("ka_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AttestationUiState())
    val uiState: StateFlow<AttestationUiState> = _uiState

    // Device capability flags (computed once)
    val hasAttestKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY)
    val hasStrongBoxFeature = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private val _settings = MutableStateFlow(
        AttestationSettings(
            hasAttestKey = hasAttestKey,
            hasStrongBox = hasStrongBoxFeature,
            isShizukuAvailable = permissionManager.isShizukuAvailableSync(),
            useAttestKey = prefs.getBoolean("use_attest_key", true),
            useStrongBox = prefs.getBoolean("use_strongbox", false),
            useShizuku = prefs.getBoolean("use_shizuku", false),
        )
    )
    val settings: StateFlow<AttestationSettings> = _settings

    init {
        performAttestation()
    }

    fun retry() = performAttestation()

    fun setUseAttestKey(enabled: Boolean) {
        prefs.edit().putBoolean("use_attest_key", enabled).apply()
        _settings.value = _settings.value.copy(useAttestKey = enabled)
        performAttestation()
    }

    fun setUseStrongBox(enabled: Boolean) {
        prefs.edit().putBoolean("use_strongbox", enabled).apply()
        _settings.value = _settings.value.copy(useStrongBox = enabled)
        performAttestation()
    }

    fun setUseShizuku(enabled: Boolean) {
        prefs.edit().putBoolean("use_shizuku", enabled).apply()
        _settings.value = _settings.value.copy(useShizuku = enabled)
        if (enabled) {
            ShizukuKeyStoreManager.bind()
        } else {
            ShizukuKeyStoreManager.unbind()
        }
        performAttestation()
    }

    /** Delete the persistent attest key (both local and remote) so it's regenerated on next run. */
    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                listOf("openmonitor_attestation_persistent", "openmonitor_attestation_tmp")
                    .filter { keyStore.containsAlias(it) }
                    .forEach { keyStore.deleteEntry(it) }
            } catch (e: Exception) {
                Log.w("KeyAttestation", "reset: failed to delete local keys", e)
            }
            // Also clear from remote Shizuku keystore if connected
            ShizukuKeyStoreManager.remoteKeyStore.value?.let { remote ->
                try { remote.deleteAllEntries() } catch (e: Exception) {
                    Log.w("KeyAttestation", "reset: failed to delete remote keys", e)
                }
            }
            performAttestation()
        }
    }

    fun refreshRevocationList() {
        viewModelScope.launch(Dispatchers.IO) {
            RevocationList.refresh()
            // Re-parse the current cert chain so revocation status reflects the updated list.
            val certs = currentCerts
            if (certs.isNotEmpty()) {
                try {
                    _uiState.value = parseCertsToUiState(certs, _settings.value)
                } catch (e: Exception) {
                    Log.w("KeyAttestation", "refreshRevocationList: re-parse failed", e)
                    _uiState.value = _uiState.value.copy(
                        revocationEntryCount = RevocationList.getEntryCount(),
                        revocationLastFetchMs = RevocationList.getLastFetchTime(),
                        revocationCacheExpiryMs = RevocationList.getCacheExpiryTime(),
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    revocationEntryCount = RevocationList.getEntryCount(),
                    revocationLastFetchMs = RevocationList.getLastFetchTime(),
                    revocationCacheExpiryMs = RevocationList.getCacheExpiryTime(),
                )
            }
        }
    }

    /** Export the current certificate chain as PKCS#7. */
    fun saveCerts(uri: Uri) {
        val certs = currentCerts
        if (certs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val factory = CertificateFactory.getInstance("X.509")
                val certPath: CertPath = factory.generateCertPath(certs)
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(certPath.getEncoded("PKCS7"))
                }
            } catch (e: Exception) {
                Log.e("KeyAttestation", "saveCerts failed", e)
            }
        }
    }

    /** Load a certificate chain from a file and display the attestation info (read-only). */
    fun loadCerts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AttestationUiState(isLoading = true)
            try {
                RootPublicKey.init(appContext)
                RevocationList.init(appContext)
                val factory = CertificateFactory.getInstance("X.509")
                val certs: List<X509Certificate> = appContext.contentResolver
                    .openInputStream(uri)?.use { input ->
                        // Try cert list first, fall back to cert path
                        try {
                            @Suppress("UNCHECKED_CAST")
                            factory.generateCertificates(input) as Collection<X509Certificate>
                        } catch (_: Exception) {
                            appContext.contentResolver.openInputStream(uri)?.use { input2 ->
                                @Suppress("UNCHECKED_CAST")
                                factory.generateCertPath(input2).certificates as List<X509Certificate>
                            } ?: emptyList()
                        }
                    }?.toList() ?: emptyList()
                if (certs.isEmpty()) throw Exception("No certificates found in file")
                currentCerts = certs
                _uiState.value = parseCertsToUiState(certs, cfg = _settings.value)
            } catch (e: Exception) {
                Log.e("KeyAttestation", "loadCerts failed", e)
                _uiState.value = AttestationUiState(isLoading = false, error = e.message ?: "Load failed")
            }
        }
    }

    // Stores the last successfully parsed cert chain for Save-to-File.
    private var currentCerts: List<X509Certificate> = emptyList()

    private fun performAttestation() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AttestationUiState(isLoading = true)
            _settings.value = _settings.value.copy(
                isShizukuAvailable = permissionManager.isShizukuAvailableSync()
            )
            val cfg = _settings.value

            try {
                RootPublicKey.init(appContext)
                RevocationList.init(appContext)

                val alias = "openmonitor_attestation_tmp"
                val attestKeyAlias = "openmonitor_attestation_persistent"
                val doAttestKey = cfg.hasAttestKey && cfg.useAttestKey
                val useShizukuService = cfg.useShizuku && cfg.isShizukuAvailable

                val remoteService: IKeyAttestKeyStore? = if (useShizukuService) {
                    withTimeoutOrNull(5_000L) {
                        ShizukuKeyStoreManager.remoteKeyStore.first { it != null }
                    }
                } else null

                val certs: List<X509Certificate> = if (remoteService != null) {
                    performRemoteAttestation(remoteService, alias, attestKeyAlias, doAttestKey, cfg.useStrongBox)
                } else {
                    performLocalAttestation(alias, attestKeyAlias, doAttestKey, cfg)
                }

                currentCerts = certs
                _uiState.value = parseCertsToUiState(certs, cfg)
            } catch (e: Exception) {
                Log.e("KeyAttestation", "Attestation failed", e)
                _uiState.value = AttestationUiState(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    /** Parse a cert chain into UI state. Used by both attestation and file load. */
    private fun parseCertsToUiState(
        certs: List<X509Certificate>,
        cfg: AttestationSettings,
    ): AttestationUiState {
        val certInfoList = mutableListOf<CertificateInfo>()
        CertificateInfo.parse(certs, certInfoList)

        val attestation = certInfoList.firstOrNull { it.attestation != null }?.attestation
            ?: throw Exception("No attestation extension found in certificate chain")

        val isTrusted = certInfoList.all { it.status == CertificateInfo.CERT_NORMAL }

        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val certChainItems = certInfoList.mapIndexed { index, info ->
            val cert = info.cert
            val revEntry = if (info.status == CertificateInfo.CERT_REVOKED) {
                RevocationList.get(cert.serialNumber)
            } else null
            val issuerTag = if (index == certInfoList.lastIndex &&
                info.issuer != RootPublicKey.Status.UNKNOWN &&
                info.issuer != RootPublicKey.Status.NULL
            ) info.issuer.name else null
            CertChainItem(
                index = index,
                // RFC2253 maps OIDs to human-readable names: SERIALNUMBER, CN, O, etc.
                subject = extractCN(cert.subjectX500Principal.getName(X500Principal.RFC2253)),
                notBefore = df.format(cert.notBefore),
                notAfter = df.format(cert.notAfter),
                statusCode = info.status,
                revocationStatus = revEntry?.status(),
                revocationReason = revEntry?.reason()?.takeIf { it.isNotEmpty() },
                issuerTag = issuerTag,
            )
        }

        val challenge = attestation.attestationChallenge
        val challengeDisplay = challenge?.let {
            val asStr = String(it)
            if (asStr.all { c -> !c.isISOControl() && c.code < 128 }) asStr
            else BaseEncoding.base16().lowerCase().encode(it)
        }

        val uniqueId = attestation.uniqueId
        val uniqueIdDisplay = if (uniqueId != null && uniqueId.isNotEmpty()) {
            BaseEncoding.base16().lowerCase().encode(uniqueId)
        } else null

        val rootOfTrust = attestation.rootOfTrust ?: attestation.teeEnforced?.rootOfTrust
        val hwFields = collectAuthFields(attestation.teeEnforced, isHardware = true)
        val swFields = collectAuthFields(attestation.softwareEnforced, isHardware = false)

        return AttestationUiState(
            isLoading = false,
            isTrusted = isTrusted,
            verifiedBootState = rootOfTrust?.let {
                RootOfTrust.verifiedBootStateToString(it.verifiedBootState)
            },
            verifiedBootStateRaw = rootOfTrust?.verifiedBootState ?: -1,
            deviceLocked = rootOfTrust?.isDeviceLocked,
            attestationVersion = Attestation.attestationVersionToString(attestation.attestationVersion),
            attestationSecurityLevel = Attestation.securityLevelToString(attestation.attestationSecurityLevel),
            keymasterVersion = Attestation.keymasterVersionToString(attestation.keymasterVersion),
            keymasterSecurityLevel = Attestation.securityLevelToString(attestation.keymasterSecurityLevel),
            challengeDisplay = challengeDisplay,
            uniqueIdDisplay = uniqueIdDisplay,
            revocationEntryCount = RevocationList.getEntryCount(),
            revocationLastFetchMs = RevocationList.getLastFetchTime(),
            revocationCacheExpiryMs = RevocationList.getCacheExpiryTime(),
            rootCertIssuer = certChainItems.lastOrNull()?.issuerTag,
            certChainInfo = certChainItems,
            authFields = hwFields + swFields,
            hasStrongBox = cfg.hasStrongBox,
        )
    }

    /**
     * Key generation via Shizuku user service running as shell UID with fixEnv().
     * This accesses the device's original hardware attestation cert chain (Keymaster 4.1).
     */
    private fun performRemoteAttestation(
        remote: IKeyAttestKeyStore,
        alias: String,
        attestKeyAlias: String,
        doAttestKey: Boolean,
        useStrongBox: Boolean,
    ): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")

        val errorBytes = remote.generateKeyPair(
            alias,
            if (doAttestKey) attestKeyAlias else null,
            useStrongBox,
        )
        if (errorBytes != null) {
            val ex = ObjectInputStream(ByteArrayInputStream(errorBytes)).readObject() as Exception
            throw ex
        }

        fun parseDerChain(bytes: ByteArray?): List<X509Certificate> {
            bytes ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            return factory.generateCertificates(ByteArrayInputStream(bytes)) as Collection<X509Certificate>
                    as List<X509Certificate>
        }

        val leafCerts = parseDerChain(remote.getCertificateChain(alias))
            .ifEmpty { throw Exception("Shizuku: empty leaf cert chain") }
        val attestCerts = if (doAttestKey) parseDerChain(remote.getCertificateChain(attestKeyAlias))
        else emptyList()

        // Clean up ephemeral leaf only; persistent attest key is kept for reuse
        try { remote.deleteEntry(alias) } catch (_: Exception) {}

        return leafCerts + attestCerts
    }

    /**
     * Key generation in the local app process (RKP or PURPOSE_ATTEST_KEY path, both via KeyMint).
     *
     * The PURPOSE_ATTEST_KEY path forces the cert chain through the device's hardware attestation
     * keys burned at manufacture time (Keymaster 4.1 on older devices), revealing any revoked
     * intermediate CAs. No Shizuku is required for this path.
     */
    private fun performLocalAttestation(
        alias: String,
        attestKeyAlias: String,
        doAttestKey: Boolean,
        cfg: AttestationSettings,
    ): List<X509Certificate> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)

        val now = Date()
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")

        fun buildAttestSpec() = KeyGenParameterSpec.Builder(
            attestKeyAlias, KeyProperties.PURPOSE_ATTEST_KEY
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setCertificateNotBefore(now)
            .setAttestationChallenge(now.toString().toByteArray())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cfg.useStrongBox && cfg.hasStrongBox) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

        // Step 1: generate (or reuse) the persistent PURPOSE_ATTEST_KEY.
        if (doAttestKey && !keyStore.containsAlias(attestKeyAlias)) {
            kpg.initialize(buildAttestSpec())
            kpg.generateKeyPair()
        }

        // Step 2: generate ephemeral leaf signing key.
        val leafSpec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setCertificateNotBefore(now)
            .setAttestationChallenge(now.toString().toByteArray())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cfg.useStrongBox && cfg.hasStrongBox) {
                    setIsStrongBoxBacked(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && doAttestKey) {
                    setAttestKeyAlias(attestKeyAlias)
                }
            }
            .build()

        try {
            kpg.initialize(leafSpec)
            kpg.generateKeyPair()
        } catch (e: Exception) {
            // The existing attest key may be from an older install (wrong PURPOSE).
            // Delete it and recreate so the leaf generation can succeed.
            if (doAttestKey && keyStore.containsAlias(attestKeyAlias)) {
                Log.w("KeyAttestation", "Attest key appears stale (wrong purpose?), recreating", e)
                keyStore.deleteEntry(attestKeyAlias)
                kpg.initialize(buildAttestSpec())
                kpg.generateKeyPair()
                // Retry leaf with freshly created attest key
                kpg.initialize(leafSpec)
                kpg.generateKeyPair()
            } else {
                throw e
            }
        }

        // Step 3: collect cert chains.
        val leafCerts = keyStore.getCertificateChain(alias)
            ?.map { it as X509Certificate }
            ?: throw Exception("Unable to get certificate chain")

        val attestCerts = if (doAttestKey && keyStore.containsAlias(attestKeyAlias)) {
            keyStore.getCertificateChain(attestKeyAlias)
                ?.map { it as X509Certificate }
                .orEmpty()
        } else emptyList()

        // Clean up ephemeral leaf only; keep attest key for reuse
        keyStore.deleteEntry(alias)

        return leafCerts + attestCerts
    }

    private fun collectAuthFields(authList: AuthorizationList?, isHardware: Boolean): List<AuthField> {
        authList ?: return emptyList()
        val f = mutableListOf<AuthField>()

        authList.purposes?.let {
            f += AuthField(R.string.ka_purposes, AuthorizationList.purposesToString(it), isHardware)
        }
        authList.algorithm?.let {
            f += AuthField(R.string.ka_algorithm, AuthorizationList.algorithmToString(it), isHardware)
        }
        authList.keySize?.let {
            f += AuthField(R.string.ka_key_size, "$it bits", isHardware)
        }
        authList.digests?.takeIf { it.isNotEmpty() }?.let {
            f += AuthField(R.string.ka_digest, AuthorizationList.digestsToString(it), isHardware)
        }
        authList.ecCurve?.let {
            f += AuthField(R.string.ka_ec_curve, AuthorizationList.ecCurveAsString(it), isHardware)
        }
        if (authList.noAuthRequired == true) {
            f += AuthField(R.string.ka_no_auth_required, "true", isHardware)
        }
        if (authList.rollbackResistance == true) {
            f += AuthField(R.string.ka_rollback_resistance, "true", isHardware)
        }
        if (authList.earlyBootOnly == true) {
            f += AuthField(R.string.ka_early_boot_only, "true", isHardware)
        }
        authList.creationDateTime?.let {
            f += AuthField(R.string.ka_creation_time, AuthorizationList.formatDate(it), isHardware)
        }
        authList.origin?.let {
            f += AuthField(R.string.ka_origin, AuthorizationList.originToString(it), isHardware)
        }
        authList.osVersion?.let { v ->
            f += AuthField(R.string.ka_os_version, "${formatOsVersion(v)} ($v)", isHardware)
        }
        authList.osPatchLevel?.let {
            f += AuthField(R.string.ka_os_patch, formatPatchLevel(it), isHardware)
        }
        authList.vendorPatchLevel?.let {
            f += AuthField(R.string.ka_vendor_patch, formatPatchLevel(it), isHardware)
        }
        authList.bootPatchLevel?.let {
            f += AuthField(R.string.ka_boot_patch, formatPatchLevel(it), isHardware)
        }
        // Root of Trust block
        authList.rootOfTrust?.let { rot ->
            f += AuthField(R.string.ka_boot_state,
                RootOfTrust.verifiedBootStateToString(rot.verifiedBootState), isHardware)
            f += AuthField(R.string.ka_device_locked, rot.isDeviceLocked.toString(), isHardware)
            f += AuthField(R.string.ka_boot_key,
                BaseEncoding.base16().lowerCase().encode(rot.verifiedBootKey),
                isHardware, isFullWidth = true)
            rot.verifiedBootHash?.let { hash ->
                f += AuthField(R.string.ka_boot_hash,
                    BaseEncoding.base16().lowerCase().encode(hash),
                    isHardware, isFullWidth = true)
            }
        }
        // Device attestation IDs
        authList.brand?.let { f += AuthField(R.string.ka_brand, it, isHardware) }
        authList.device?.let { f += AuthField(R.string.ka_device, it, isHardware) }
        authList.product?.let { f += AuthField(R.string.ka_product, it, isHardware) }
        authList.manufacturer?.let { f += AuthField(R.string.ka_manufacturer, it, isHardware) }
        authList.model?.let { f += AuthField(R.string.ka_model, it, isHardware) }

        return f
    }

    /** Extract CN value from an X.500 DN string, or return first 60 chars of DN. */
    private fun extractCN(dn: String): String {
        val cn = Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1)
        return cn ?: dn.take(60)
    }

    private fun formatOsVersion(v: Int): String {
        if (v == 0) return "0"
        return "${v / 10000}.${(v % 10000) / 100}.${v % 100}"
    }

    private fun formatPatchLevel(v: Int): String {
        if (v == 0) return "0"
        return "${v / 100}-${(v % 100).toString().padStart(2, '0')}"
    }
}
