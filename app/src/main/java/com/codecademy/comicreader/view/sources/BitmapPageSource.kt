package com.codecademy.comicreader.view.sources

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

abstract class BitmapPageSource(dispatcher: CoroutineDispatcher = Dispatchers.IO) : ComicPageSource {

    // Cache
    protected val bitmapCache = object : LruCache<Int, Bitmap>(5) {}

    // Coroutine
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Track async loading tasks
    private val jobs = ConcurrentHashMap<Int, Job>()

    // Cache helpers
    protected fun cache(index: Int, bmp: Bitmap) {
        if (!bmp.isRecycled) bitmapCache.put(index, bmp)
    }

    protected fun getCached(index: Int): Bitmap? {
        return bitmapCache.get(index)?.takeIf { !it.isRecycled }
    }

    // Cancel
    override fun cancelLoad(index: Int) {
        jobs.remove(index)?.cancel()
    }

    // Async page load
    override fun loadPageAsync(index: Int, callback: (Bitmap?) -> Unit): Job {
        // Cancel any previous task
        jobs[index]?.cancel()

        val job = scope.launch {
            // Check cache first
            getCached(index)?.let {
                withContext(Dispatchers.Main) { callback(it) }
                jobs.remove(index)
                return@launch
            }

            // Load page bitmap
            val bmp = try { getPageBitmap(index) }
            catch (_: CancellationException) { null }
            catch (t: Throwable) {
                Log.e("BitmapPageSource", "Error decoding page $index", t)
                null
            }

            if (!isActive) return@launch

            bmp?.let { cache(index, it) }

            withContext(Dispatchers.Main) { callback(bmp) }

            jobs.remove(index)
        }

        jobs[index] = job
        return job
    }

    // Close
    override fun closeSource() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
        bitmapCache.evictAll()
    }

    // Abstract
    abstract override fun getPageBitmap(index: Int): Bitmap?

    // Shared corrupt placeholder
    protected fun createCorruptPlaceholder(msg: String): Bitmap {
        val width = 800
        val height = 1200
        val bmp = createBitmap(width, height)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)

        val paint = Paint().apply {
            color = Color.RED
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(msg, width / 2f, height / 2f, paint)

        return bmp
    }
}