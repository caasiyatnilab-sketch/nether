package com.example.utils

import android.app.ActivityManager
import android.content.Context
import android.util.Log

object DeviceHelper {
    private const val TAG = "DeviceHelper"

    /**
     * Get total device RAM in GB
     */
    fun getTotalRamGb(context: Context): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMemory / (1024f * 1024f * 1024f)).toFloat()
    }

    /**
     * Get available device RAM in GB
     */
    fun getAvailableRamGb(context: Context): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.availMem / (1024f * 1024f * 1024f)).toFloat()
    }

    /**
     * Get recommended models based on device RAM
     * Returns list of model IDs in order of preference
     */
    fun getRecommendedModels(context: Context): List<String> {
        val totalRam = getTotalRamGb(context)
        Log.i(TAG, "Device RAM: ${String.format("%.1f", totalRam)} GB")

        return when {
            totalRam >= 12f -> {
                // Flagship: Can handle heavy models
                listOf(
                    "llama_3_2_3b",      // Primary recommendation
                    "qwen2_3b",
                    "phi_2_2_7b",
                    "gemma_2b",
                    "tinyllama_1_1b"
                )
            }
            totalRam >= 8f -> {
                // Mid-high range: Balanced models
                listOf(
                    "llama_3_2_3b",      // Primary recommendation
                    "qwen2_3b",
                    "phi_2_2_7b",
                    "gemma_2b",
                    "tinyllama_1_1b"
                )
            }
            totalRam >= 6f -> {
                // Mid-range: Conservative selection
                listOf(
                    "gemma_2b",          // Primary recommendation
                    "phi_2_2_7b",
                    "tinyllama_1_1b"
                )
            }
            else -> {
                // Budget: Ultra-light only
                listOf("tinyllama_1_1b")
            }
        }
    }

    /**
     * Check if device has enough RAM for specific model
     */
    fun canRunModel(context: Context, minRamRequired: Float): Boolean {
        val totalRam = getTotalRamGb(context)
        val availableRam = getAvailableRamGb(context)

        // Need at least minRamRequired available for model + overhead
        return availableRam >= (minRamRequired * 0.8f) // 80% of required (20% buffer for system)
    }

    /**
     * Get device tier classification
     */
    fun getDeviceTier(context: Context): DeviceTier {
        val totalRam = getTotalRamGb(context)

        return when {
            totalRam >= 12f -> DeviceTier.FLAGSHIP
            totalRam >= 8f -> DeviceTier.MID_HIGH
            totalRam >= 6f -> DeviceTier.MID_RANGE
            else -> DeviceTier.BUDGET
        }
    }

    /**
     * Get device tier description for UI
     */
    fun getDeviceTierDescription(context: Context): String {
        val totalRam = getTotalRamGb(context)
        val tier = getDeviceTier(context)

        return when (tier) {
            DeviceTier.FLAGSHIP -> "🚀 Flagship (${String.format("%.1f", totalRam)}GB) - All models supported"
            DeviceTier.MID_HIGH -> "⚡ Mid-High Range (${String.format("%.1f", totalRam)}GB) - Most models supported"
            DeviceTier.MID_RANGE -> "📱 Mid-Range (${String.format("%.1f", totalRam)}GB) - Light to medium models"
            DeviceTier.BUDGET -> "💰 Budget (${String.format("%.1f", totalRam)}GB) - Ultra-light models only"
        }
    }

    /**
     * Estimate available time before OOM for a given model
     */
    fun getEstimatedRuntimeMinutes(context: Context, modelSizeGb: Float): Int {
        val availableRam = getAvailableRamGb(context)
        val modelWithOverhead = modelSizeGb * 1.5f // 50% overhead for inference buffers

        return if (modelWithOverhead <= availableRam) {
            (availableRam / modelWithOverhead * 30).toInt() // Rough estimate
        } else {
            0 // Not enough RAM
        }
    }

    enum class DeviceTier {
        FLAGSHIP,    // 12GB+
        MID_HIGH,    // 8-12GB
        MID_RANGE,   // 6-8GB
        BUDGET       // <6GB
    }
}
