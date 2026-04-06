package com.cloudorz.openmonitor.core.data.datasource

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log

/**
 * Creates a temporary EGL context to query OpenGL ES strings.
 * Same approach as DevCheck's EglUtils.cpp and Athena's EglInformation.
 */
object EglHelper {

    private const val TAG = "EglHelper"

    data class GlStrings(
        val renderer: String = "",
        val version: String = "",
        val vendor: String = "",
        val extensionsCount: Int = 0,
    )

    data class FullGlInfo(
        val renderer: String = "",
        val version: String = "",
        val vendor: String = "",
        val shadingLanguageVersion: String = "",
        val extensionsCount: Int = 0,
        val extensions: List<String> = emptyList(),
        val eglVersion: String = "",
        val eglVendor: String = "",
        val eglClientApis: String = "",
        val eglExtensions: List<String> = emptyList(),
    )

    fun queryFullGlInfo(): FullGlInfo {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return FullGlInfo()

            val majorMinor = IntArray(2)
            if (!EGL14.eglInitialize(display, majorMinor, 0, majorMinor, 1)) return FullGlInfo()

            // EGL info
            val eglVersion = EGL14.eglQueryString(display, EGL14.EGL_VERSION) ?: ""
            val eglVendor = EGL14.eglQueryString(display, EGL14.EGL_VENDOR) ?: ""
            val eglClientApis = EGL14.eglQueryString(display, EGL14.EGL_CLIENT_APIS) ?: ""
            val eglExtStr = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS) ?: ""
            val eglExts = if (eglExtStr.isNotBlank()) eglExtStr.split(" ").filter { it.isNotBlank() }.sorted() else emptyList()

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) return FullGlInfo()
            if (numConfigs[0] == 0 || configs[0] == null) return FullGlInfo()

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0]!!, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return FullGlInfo()

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return FullGlInfo()

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return FullGlInfo()

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            val version = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
            val shadingLangVer = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION) ?: ""
            val extensionsStr = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            val extensions = if (extensionsStr.isNotBlank()) extensionsStr.split(" ").filter { it.isNotBlank() }.sorted() else emptyList()

            return FullGlInfo(
                renderer = renderer,
                version = version,
                vendor = vendor,
                shadingLanguageVersion = shadingLangVer,
                extensionsCount = extensions.size,
                extensions = extensions,
                eglVersion = eglVersion,
                eglVendor = eglVendor,
                eglClientApis = eglClientApis,
                eglExtensions = eglExts,
            )
        } catch (e: Exception) {
            Log.d(TAG, "queryFullGlInfo failed", e)
            return FullGlInfo()
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                EGL14.eglTerminate(display)
            }
        }
    }

    fun queryGlStrings(): GlStrings {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            // 1. Get default display
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return GlStrings()

            val majorMinor = IntArray(2)
            if (!EGL14.eglInitialize(display, majorMinor, 0, majorMinor, 1)) return GlStrings()

            // 2. Choose config
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                return GlStrings()
            }
            if (numConfigs[0] == 0 || configs[0] == null) return GlStrings()

            // 3. Create PBuffer surface (1x1, offscreen)
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE,
            )
            surface = EGL14.eglCreatePbufferSurface(display, configs[0]!!, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return GlStrings()

            // 4. Create OpenGL ES 2.0 context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE,
            )
            context = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return GlStrings()

            // 5. Make current
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return GlStrings()

            // 6. Query GL strings
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            val version = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            val extCount = if (extensions.isNotBlank()) extensions.split(" ").count { it.isNotBlank() } else 0

            return GlStrings(
                renderer = renderer,
                version = version,
                vendor = vendor,
                extensionsCount = extCount,
            )
        } catch (e: Exception) {
            Log.d(TAG, "queryGlStrings failed", e)
            return GlStrings()
        } finally {
            // Cleanup
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                EGL14.eglTerminate(display)
            }
        }
    }
}
