package com.codecademy.comicreader.view.sources

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.codecademy.comicreader.view.sources.ComicPageSource

abstract class BitmapPageSource : ComicPageSource {
    protected val bitmapCache: LruCache<Int, Bitmap> =
        LruCache<Int, Bitmap>(5) // Tune size per device

    protected fun cache(index: Int, bmp: Bitmap) {
        if (!bmp.isRecycled) {
            bitmapCache.put(index, bmp)
        }
    }

    protected fun getCached(index: Int): Bitmap? {
        val bmp = bitmapCache.get(index)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun clear() {
        bitmapCache.evictAll()
    }

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