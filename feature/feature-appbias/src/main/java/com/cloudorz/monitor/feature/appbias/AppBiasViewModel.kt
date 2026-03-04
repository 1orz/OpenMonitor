package com.cloudorz.monitor.feature.appbias

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.database.dao.AppConfigDao
import com.cloudorz.monitor.core.database.entity.AppConfigEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppBiasItem(
    val packageName: String,
    val appName: String,
    val configCount: Int,
    val config: AppConfigEntity? = null,
)

data class AppBiasUiState(
    val apps: List<AppBiasItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class AppBiasViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigDao: AppConfigDao,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val selectedPackage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppBiasUiState> = combine(
        installedApps,
        appConfigDao.getAllConfigs(),
        searchQuery,
    ) { apps, configs, query ->
        val configMap = configs.associateBy { it.packageName }

        val items = apps.map { (packageName, appName) ->
            val config = configMap[packageName]
            val count = if (config != null) countActiveConfigs(config) else 0
            AppBiasItem(
                packageName = packageName,
                appName = appName,
                configCount = count,
                config = config,
            )
        }.let { list ->
            if (query.isBlank()) list
            else {
                val lowerQuery = query.lowercase()
                list.filter { item ->
                    item.appName.lowercase().contains(lowerQuery) ||
                        item.packageName.lowercase().contains(lowerQuery)
                }
            }
        }.sortedWith(
            compareByDescending<AppBiasItem> { it.configCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
        )

        AppBiasUiState(
            apps = items,
            searchQuery = query,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppBiasUiState(),
    )

    /** The package name of the app whose detail sheet is currently shown, or null. */
    val selectedApp: StateFlow<String?> = selectedPackage

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isLaunchable(pm, it.packageName) }
                    .map { appInfo ->
                        val label = appInfo.loadLabel(pm).toString()
                        appInfo.packageName to label
                    }
                    .sortedBy { it.second.lowercase() }
            }
            installedApps.value = apps
        }
    }

    private fun isLaunchable(pm: PackageManager, packageName: String): Boolean {
        return pm.getLaunchIntentForPackage(packageName) != null
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onAppSelected(packageName: String) {
        selectedPackage.value = packageName
    }

    fun onDetailDismissed() {
        selectedPackage.value = null
    }

    fun saveConfig(config: AppConfigEntity) {
        viewModelScope.launch {
            appConfigDao.insertOrUpdate(config)
        }
    }

    fun deleteConfig(packageName: String) {
        viewModelScope.launch {
            appConfigDao.deleteConfig(packageName)
        }
    }

    companion object {
        fun countActiveConfigs(config: AppConfigEntity): Int {
            var count = 0
            if (config.aloneLight) count++
            if (config.disNotice) count++
            if (config.disButton) count++
            if (config.gpsOn) count++
            if (config.freeze) count++
            if (config.screenOrientation != -1) count++
            if (config.showMonitor) count++
            return count
        }
    }
}
