package com.codecademy.comicreader.utils

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object SystemUtil {
    fun getRamInGB(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024L * 1024L * 1024L)).toInt() // bytes to GB
    }

    // Use Coroutine Dispatcher instead of ExecutorService
    @JvmStatic
    fun createSmartDispatcher(context: Context): CoroutineDispatcher {
        val cores = Runtime.getRuntime().availableProcessors()
        val ram = getRamInGB(context)
        val recommendedThreads = when {
            cores <= 2 || ram <= 2 -> 1
            cores <= 4 || ram <= 3 -> 2
            else -> 3
        }

        return Executors.newFixedThreadPool(recommendedThreads).asCoroutineDispatcher()
    }
}