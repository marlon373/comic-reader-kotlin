package com.codecademy.comicreader.view.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap

class PDFPageSource(
    context: Context,
    uri: Uri
) : BitmapPageSource() {

    private val renderer: PdfRenderer

    init {
        renderer = try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Unable to open PDF Uri: $uri")
            PdfRenderer(fd)
        } catch (e: Exception) {
            throw RuntimeException("Failed to open PDF Uri", e)
        }
    }

    override fun getPageCount(): Int {
        return renderer.pageCount
    }

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
                val scale = minOf(
                    maxSize.toFloat() / width,
                    maxSize.toFloat() / height
                )
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

    fun close() {
        renderer.close()
    }
}