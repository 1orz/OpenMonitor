# CloudMonitor ProGuard rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.cloudorz.monitor.core.database.entity.** { *; }

# Keep Shizuku
-keep class rikka.shizuku.** { *; }

# Keep libsu
-keep class com.topjohnwu.superuser.** { *; }

# Keep JNI native methods (cpuinfo bridge)
-keep class com.cloudorz.monitor.core.common.CpuNativeInfo {
    native <methods>;
}

# Keep PowerProfile for reflection (battery capacity reading)
-keep class com.android.internal.os.PowerProfile {
    public <methods>;
}
