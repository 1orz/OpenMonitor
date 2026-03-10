# CloudMonitor ProGuard rules

# Keep Hilt-generated components and entry points
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep @dagger.hilt.android.EarlyEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.cloudorz.openmonitor.core.database.entity.** { *; }

# Keep Shizuku
-keep class rikka.shizuku.** { *; }

# Keep libsu
-keep class com.topjohnwu.superuser.** { *; }

# Keep Service subclasses (FloatMonitorService extends LifecycleService)
-keep class * extends android.app.Service

# Keep JNI native methods (cpuinfo bridge)
-keep class com.cloudorz.openmonitor.core.common.CpuNativeInfo {
    native <methods>;
}

# PowerProfile is an internal Android API used via reflection for battery capacity
-dontwarn com.android.internal.os.PowerProfile
