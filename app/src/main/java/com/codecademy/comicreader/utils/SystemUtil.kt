package com.codecademy.comicreader.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

object SystemUtil {

    /**
     * Returns total device RAM in GB.
     * @param context Application or Activity context
     * @return Total RAM rounded down to GB
     */
    fun getRamInGB(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        // Convert bytes → GB
        return (info.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    /**
     * Determines an optimal IO thread count based on:
     * - CPU cores
     * - Total RAM
     * - ARM64 support
     * This is optimized for IO-heavy tasks:
     * - CBR/CBZ extraction
     * - Image decoding
     * - Disk caching
     */
    fun getRecommendedIOThreadCount(context: Context): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val ram = getRamInGB(context)
        val isArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }

        return when {

            // Ultra high-end devices
            cores >= 8 && ram >= 16 -> 6
            cores >= 8 && ram >= 8 -> 5

            // High-end devices
            isArm64 && ram >= 6 -> 4
            isArm64 && ram >= 4 -> 3

            // Mid-range / low-end
            cores <= 2 || ram <= 2 -> 1
            cores <= 4 || ram <= 3 -> 2

            // Safe default
            else -> 3
        }
    }

    /**
     * Returns a conservative thread count for
     * CPU-light background tasks.
     * Limits max threads to 3 to avoid:
     * - UI jank
     * - GC pressure
     * - Battery drain
     */
    fun getThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return max(1, min(cores / 2, 3))
    }

    /**
     * Creates an Coroutines optimized for IO work.
     * Use this for:
     * - Archive extraction
     * - Image decoding
     * - File reads/writes
     */
    fun createIODispatcher(context: Context): CoroutineDispatcher {
        val threads = getRecommendedIOThreadCount(context)
        return Executors.newFixedThreadPool(threads).asCoroutineDispatcher()
    }

    /**
     * Creates a general-purpose Coroutines.
     * Use this for:
     * - Lightweight background tasks
     * - Non-blocking operations
     */
    fun createDispatcher(): CoroutineDispatcher {
        val threads = getThreadCount()
        return Executors.newFixedThreadPool(threads).asCoroutineDispatcher()
    }
}
