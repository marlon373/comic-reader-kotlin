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
import java.nio.channels.FileChannel

/**
 * CBRPageSource - reads RAR archives and exposes pages as Bitmap
 */
class CBRPageSource(context: Context, uri: Uri) : BitmapPageSource() {

    private val pfd: ParcelFileDescriptor
    private val channel: FileChannel
    private val inStream: MappedFileInStream
    private val archive: IInArchive
    private val imageIndices: List<Int>

    init {
        // Keep ParcelFileDescriptor alive for the lifetime of this source
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw RuntimeException("Cannot open CBR URI: $uri")

        channel = FileInputStream(pfd.fileDescriptor).channel
        inStream = MappedFileInStream(channel)

        SevenZip.initSevenZipFromPlatformJAR()
        archive = SevenZip.openInArchive(null, inStream)

        // Build index list of image item indices (ordered)
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
        if (index !in imageIndices.indices) return null

        // Extract item bytes in-memory (only for requested page)
        return try {
            val itemIndex = imageIndices[index]
            val baos = ByteArrayOutputStream()

            // NOTE: native extract may be blocking; called from IO dispatcher in loadPageAsync
            archive.extractSlow(itemIndex) { data ->
                baos.write(data)
                data.size
            }

            val bytes = baos.toByteArray()
            // decode with inSampleSize to reduce memory
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSizeForTarget(bytes.size)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            bmp?.also { cache(index, it) } ?: createCorruptPlaceholder("Corrupt page $index")
        } catch (e: Exception) {
            Log.e("CBRPageSource", "Failed to decode page $index", e)
            createCorruptPlaceholder("Failed page $index")
        }
    }

    // Close resources, cancel outstanding loads
    override fun closeSource() {
        super.closeSource()
        try { archive.close() } catch (_: Exception) {}
        try { inStream.close() } catch (_: Exception) {}
        try { channel.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }

    // Helper: basic heuristic to set inSampleSize based on compressed bytes
    private fun calculateInSampleSizeForTarget(byteCount: Int): Int {
        // grow sample size for large images — tune as needed
        return when {
            byteCount > 5_000_000 -> 4
            byteCount > 2_000_000 -> 3
            byteCount > 800_000 -> 2
            else -> 1
        }
    }
}
