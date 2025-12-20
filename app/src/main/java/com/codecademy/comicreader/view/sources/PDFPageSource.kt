package com.codecademy.comicreader.view.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap

/**
 * PDFPageSource - exposes PDF pages as Bitmaps
 */
class PDFPageSource(context: Context, uri: Uri) : BitmapPageSource() {

    private val pfd: ParcelFileDescriptor
    private val renderer: PdfRenderer

    init {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Unable to open PDF Uri: $uri")
        renderer = PdfRenderer(pfd)
    }

    override fun getPageCount(): Int = renderer.pageCount

    @Synchronized
    override fun getPageBitmap(index: Int): Bitmap? {
        getCached(index)?.let { return it }

        return try {
            renderer.openPage(index).use { page ->
                val width = page.width
                val height = page.height
                if (width <= 0 || height <= 0) {
                    return createCorruptPlaceholder("Invalid page $index")
                }

                val maxSize = 1280
                val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
                val scaledWidth = maxOf(1, (width * scale).toInt())
                val scaledHeight = maxOf(1, (height * scale).toInt())

                val bmp = createBitmap(scaledWidth, scaledHeight)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache(index, bmp)
                bmp
            }
        } catch (e: Exception) {
            Log.e("PDFPageSource", "Error rendering page $index", e)
            createCorruptPlaceholder("Error page $index")
        }
    }

    override fun closeSource() {
        super.closeSource()
        try { renderer.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}
