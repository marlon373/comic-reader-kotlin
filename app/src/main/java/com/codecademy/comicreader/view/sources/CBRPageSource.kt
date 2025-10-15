package com.codecademy.comicreader.view.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class CBRPageSource(context: Context, uri: Uri) : BitmapPageSource() {
    private val tempFile: File
    private val archive: Archive
    private val imageHeaders: MutableList<FileHeader>

    init {
        this.tempFile = saveTempFile(context, uri)

        try {
            this.archive = Archive(tempFile)
        } catch (e: Exception) {
            throw RuntimeException("Failed to open .cbr archive", e)
        }

        this.imageHeaders = archive.fileHeaders
            .filter { h: FileHeader ->
                !h.isDirectory && h.fileName.lowercase(Locale.getDefault())
                    .matches(".*\\.(jpg|jpeg|png|webp)$".toRegex())
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
            .toMutableList()
    }

    private fun saveTempFile(context: Context, uri: Uri): File {
        try {
            val temp = File.createTempFile("comic_", ".cbr", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { `in` ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while ((`in`.read(buffer).also { len = it }) > 0) {
                        out.write(buffer, 0, len)
                    }
                }
            }
            return temp
        } catch (e: IOException) {
            throw RuntimeException("Failed to save temp CBR file", e)
        }
    }

    override fun getPageCount(): Int = imageHeaders.size

    @Synchronized
    override fun getPageBitmap(index: Int): Bitmap? {
        getCached(index)?.let { return it }
        if (index !in imageHeaders.indices) return null

        return try {
            val header = imageHeaders[index]
            archive.getInputStream(header).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(inputStream, null, options)
                if (bmp != null) {
                    cache(index, bmp)
                    bmp
                } else {
                    createCorruptPlaceholder("Corrupt page $index")
                }
            }
        } catch (e: Exception) {
            Log.e("CBRPageSource", "Failed to decode page $index", e)
            createCorruptPlaceholder("Failed page $index")
        }
    }

    fun close() {
        try {
            archive.close()
        } catch (e: IOException) {
            Log.e("CBRPageSource", "Failed to close archive", e)
        }
        if (tempFile.exists()) {
            val deleted = tempFile.delete()
            if (!deleted) {
                Log.w("CBRPageSource", "Failed to delete temp file: ${tempFile.absolutePath}")
            }
        }
    }
}