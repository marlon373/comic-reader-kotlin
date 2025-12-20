package com.codecademy.comicreader.view.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.codecademy.comicreader.utils.MappedFileInStream
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

/**
 * CBZPageSource - reads ZIP/CBZ archives and exposes pages as Bitmap
 */
class CBZPageSource(context: Context, uri: Uri) : BitmapPageSource() {

    private val pfd: ParcelFileDescriptor
    private val inStream: MappedFileInStream
    private val archive: IInArchive
    private val imageIndices: List<Int>

    init {
        // Keep PFD alive
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw RuntimeException("Cannot open CBZ URI: $uri")

        val channel = FileInputStream(pfd.fileDescriptor).channel
        inStream = MappedFileInStream(channel)

        SevenZip.initSevenZipFromPlatformJAR()
        archive = SevenZip.openInArchive(null, inStream)

        // Get indices of image entries
        imageIndices = (0 until archive.numberOfItems)
            .filter { i ->
                val isFolder = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                if (isFolder) return@filter false
                val path = (archive.getProperty(i, PropID.PATH)?.toString() ?: "").lowercase()
                path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                        path.endsWith(".png") || path.endsWith(".webp")
            }
            .sortedBy { archive.getProperty(it, PropID.PATH).toString() }
    }

    override fun getPageCount(): Int = imageIndices.size

    @Synchronized
    override fun getPageBitmap(index: Int): Bitmap? {
        getCached(index)?.let { return it }
        if (index !in imageIndices.indices) return createCorruptPlaceholder("Missing page $index")

        return try {
            val itemIndex = imageIndices[index]
            val baos = ByteArrayOutputStream()

            archive.extractSlow(itemIndex) { data ->
                baos.write(data)
                data.size
            }

            val bytes = baos.toByteArray()
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            bmp?.also { cache(index, it) } ?: createCorruptPlaceholder("Corrupt page $index")
        } catch (e: Exception) {
            Log.e("CBZPageSource", "Failed to decode page $index", e)
            createCorruptPlaceholder("Failed page $index")
        }
    }

    override fun closeSource() {
        try { archive.close() } catch (_: Exception) {}
        try { inStream.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}

