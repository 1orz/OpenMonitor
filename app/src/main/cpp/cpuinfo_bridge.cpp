#include <cstring>
#include <jni.h>
#include <cinttypes>
#include <android/log.h>
#include "cpuinfo.h"
#include <string>

#define LOG_TAG "cpuinfo-bridge"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeInit(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize()) {
        LOGI("cpuinfo initialization failed");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetCpuName(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize()) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(cpuinfo_get_package(0)->name);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetCoreCount(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize()) {
        return 0;
    }
    return cpuinfo_get_cores_count();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeHasArmNeon(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize()) {
        return JNI_FALSE;
    }
    return cpuinfo_has_arm_neon() ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetL1dCaches(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize() || cpuinfo_get_l1d_caches_count() == 0) {
        return nullptr;
    }
    uint32_t count = cpuinfo_get_l1d_caches_count();
    jintArray result = env->NewIntArray(count);
    jint *buf = new jint[count];
    auto caches = cpuinfo_get_l1d_caches();
    for (uint32_t i = 0; i < count; i++) {
        buf[i] = caches[i].size;
    }
    env->SetIntArrayRegion(result, 0, count, buf);
    delete[] buf;
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetL1iCaches(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize() || cpuinfo_get_l1i_caches_count() == 0) {
        return nullptr;
    }
    uint32_t count = cpuinfo_get_l1i_caches_count();
    jintArray result = env->NewIntArray(count);
    jint *buf = new jint[count];
    auto caches = cpuinfo_get_l1i_caches();
    for (uint32_t i = 0; i < count; i++) {
        buf[i] = caches[i].size;
    }
    env->SetIntArrayRegion(result, 0, count, buf);
    delete[] buf;
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetL2Caches(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize() || cpuinfo_get_l2_caches_count() == 0) {
        return nullptr;
    }
    uint32_t count = cpuinfo_get_l2_caches_count();
    jintArray result = env->NewIntArray(count);
    jint *buf = new jint[count];
    auto caches = cpuinfo_get_l2_caches();
    for (uint32_t i = 0; i < count; i++) {
        buf[i] = caches[i].size;
    }
    env->SetIntArrayRegion(result, 0, count, buf);
    delete[] buf;
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cloudorz_monitor_core_common_CpuNativeInfo_nativeGetL3Caches(JNIEnv *env, jobject thiz) {
    if (!cpuinfo_initialize() || cpuinfo_get_l3_caches_count() == 0) {
        return nullptr;
    }
    uint32_t count = cpuinfo_get_l3_caches_count();
    jintArray result = env->NewIntArray(count);
    jint *buf = new jint[count];
    auto caches = cpuinfo_get_l3_caches();
    for (uint32_t i = 0; i < count; i++) {
        buf[i] = caches[i].size;
    }
    env->SetIntArrayRegion(result, 0, count, buf);
    delete[] buf;
    return result;
}
