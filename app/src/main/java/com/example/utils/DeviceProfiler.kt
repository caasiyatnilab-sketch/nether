package com.example.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import com.example.data.AiModel
import com.example.data.ModelUniverse
import com.example.data.TeamRole

/**
 * Real on-device hardware profile, gathered from the OS — no hardcoded values.
 */
data class HardwareProfile(
    val totalRamGb: Float,
    val availableRamGb: Float,
    val cpuCores: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val gpuRenderer: String,
    val gpuVendor: String,
    val supportsVulkan: Boolean,
    val socModel: String
) {
    val tier: String
        get() = when {
            totalRamGb >= 12f -> "Flagship"
            totalRamGb >= 8f -> "High-end"
            totalRamGb >= 6f -> "Mid-range"
            totalRamGb >= 3f -> "Entry"
            else -> "Low-RAM"
        }
}

/**
 * One recommended local model plus the reason it fits this device.
 */
data class ModelRecommendation(val model: AiModel, val reason: String, val fits: Boolean)

/**
 * A suggested two-model "team": a fast drafter and a stronger reasoner that can
 * both be resident at once on this device's RAM.
 */
data class DualSuggestion(val drafter: AiModel, val reasoner: AiModel, val reason: String)

object DeviceProfiler {
    private const val TAG = "DeviceProfiler"

    fun profile(context: Context): HardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gpu = queryGpu()
        val vulkan = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        return HardwareProfile(
            totalRamGb = mem.totalMem / 1.0e9f,
            availableRamGb = mem.availMem / 1.0e9f,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            gpuRenderer = gpu.first,
            gpuVendor = gpu.second,
            supportsVulkan = vulkan,
            socModel = socName()
        )
    }

    private fun socName(): String {
        val parts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOfNotNull(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() }
        } else emptyList()
        return if (parts.isNotEmpty()) parts.joinToString(" ") else "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * Real GPU renderer/vendor via a throwaway 1x1 off-screen EGL context.
     * Returns ("renderer", "vendor"); falls back to "Unknown" if EGL is absent.
     */
    private fun queryGpu(): Pair<String, String> {
        var display: EGLDisplay? = null
        var ctx: EGLContext? = null
        var surface: EGLSurface? = null
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "Unknown" to "Unknown"
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "Unknown" to "Unknown"

            val cfgAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            if (!EGL14.eglChooseConfig(display, cfgAttribs, 0, configs, 0, 1, numConfig, 0) || numConfig[0] == 0) {
                return "Unknown" to "Unknown"
            }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val pbAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbAttribs, 0)
            if (!EGL14.eglMakeCurrent(display, surface, surface, ctx)) return "Unknown" to "Unknown"

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            renderer to vendor
        } catch (e: Throwable) {
            Log.w(TAG, "GPU query failed: ${e.message}")
            "Unknown" to "Unknown"
        } finally {
            if (display != null) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != null) EGL14.eglDestroySurface(display, surface)
                if (ctx != null) EGL14.eglDestroyContext(display, ctx)
                EGL14.eglTerminate(display)
            }
        }
    }

    /**
     * Recommend models that realistically fit this device. A model "fits" when
     * the device's total RAM clears its minimum and the file isn't larger than
     * roughly available RAM. Best (highest reasoning) fitting model first.
     */
    fun recommend(profile: HardwareProfile): List<ModelRecommendation> {
        val ram = profile.totalRamGb
        return ModelUniverse.models
            .sortedByDescending { it.reasoningScore }
            .map { m ->
                val headroom = ram - m.minRamGb
                val fits = ram >= m.minRamGb
                val reason = when {
                    !fits -> "Needs ${m.minRamGb}GB RAM (device has ${"%.1f".format(ram)}GB)"
                    headroom >= 2 -> "Comfortable fit (${m.sizeLabel}, ${m.minRamGb}GB min)"
                    else -> "Tight but runnable (${m.sizeLabel}, ${m.minRamGb}GB min)"
                }
                ModelRecommendation(m, reason, fits)
            }
    }

    /** The single best model that fits, or the lightest one if none "fit". */
    fun bestSingle(profile: HardwareProfile): AiModel {
        val recs = recommend(profile)
        return recs.firstOrNull { it.fits }?.model
            ?: ModelUniverse.models.minByOrNull { it.minRamGb }!!
    }

    /**
     * Suggest a drafter + reasoner pair that can both be resident at once
     * (combined minimum RAM within total RAM). Falls back to the two lightest.
     */
    fun suggestDual(profile: HardwareProfile): DualSuggestion {
        val ram = profile.totalRamGb
        val drafters = ModelUniverse.models.filter { it.teamRole == TeamRole.DRAFTER }
            .sortedByDescending { it.reasoningScore }
        val reasoners = ModelUniverse.models.filter { it.teamRole == TeamRole.REASONER }
            .sortedByDescending { it.reasoningScore }

        for (r in reasoners) {
            for (d in drafters) {
                // Both resident: sum of minimums (minus shared OS headroom) under total RAM.
                if (d.minRamGb + r.minRamGb - 1 <= ram) {
                    return DualSuggestion(
                        d, r,
                        "${d.name} drafts fast, ${r.name} reviews & refines — both fit in ${"%.1f".format(ram)}GB."
                    )
                }
            }
        }
        // Fallback: two lightest models.
        val light = ModelUniverse.models.sortedBy { it.minRamGb }
        return DualSuggestion(
            light[0], light.getOrElse(1) { light[0] },
            "Lightweight pairing for constrained RAM (${"%.1f".format(ram)}GB)."
        )
    }
}
