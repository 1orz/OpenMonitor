package com.cloudorz.monitor.core.model.app

enum class AppType(val displayName: String) {
    USER("User"),
    SYSTEM("System"),
    UNKNOWN("Unknown"),
}

data class AppInfo(
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val appType: AppType = AppType.UNKNOWN,
    val enabled: Boolean = true,
    val suspended: Boolean = false,
    val updated: Boolean = false,
    val targetSdkVersion: Int = 0,
    val minSdkVersion: Int = 0,
    val description: String = "",
    val path: String = "",
    val dir: String = "",
    val stateTags: List<String> = emptyList(),
) {
    val isSystemApp: Boolean
        get() = appType == AppType.SYSTEM

    val isUserApp: Boolean
        get() = appType == AppType.USER

    val displayName: String
        get() = appName.ifEmpty { packageName }
}
