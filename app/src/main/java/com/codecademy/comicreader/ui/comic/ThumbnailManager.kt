package com.codecademy.comicreader.ui.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import com.codecademy.comicreader.utils.MappedFileInStream
import com.codecademy.comicreader.utils.SystemUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import java.io.BufferedInputStream
import java.io.File

import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import androidx.core.graphics.createBitmap

/**
 * ThumbnailManager - kotlin version with cancellable jobs
 * Supports PDF / CBZ / CBR thumbnails
 * Uses memory cache + disk cache
 * Uses Coroutines for background tasks
 */
object ThumbnailManager {

    // Memory cache
    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {}

    // Disk cleanup guard
    private var cleanupDone = false

    // DISPATCHER + SCOPE
    // Dedicated thread pool for thumbnails, prevents UI lag.
    private lateinit var scope: CoroutineScope

    fun init() {
        if (::scope.isInitialized) return
        val dispatcher = SystemUtil.createDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    // Disk path for thumbnail cache
    private fun getDiskThumbnail(context: Context, uri: Uri): File {
        val name = uri.lastPathSegment?.substringAfterLast("/") ?: "thumb"
        return File(context.cacheDir, "${name}_thumb.jpg")
    }

    //  Public API: load thumbnail + return Cancellable job
    fun loadThumbnailAsync(
        context: Context,
        uri: Uri,
        type: String,
        imageView: ImageView,
        placeholderRes: Int? = null,
        maxAgeDays: Int = 1
    ): Job {
        if (!::scope.isInitialized) init()
        val key = uri.toString()
        imageView.tag = key

        if (!cleanupDone) cleanupOldThumbnails(context.cacheDir, maxAgeDays)

        val thumbFile = getDiskThumbnail(context, uri)

        // Show memory cache or placeholder immediately to avoid flicker
        memoryCache.get(key)?.let { bmp ->
            imageView.setImageBitmap(bmp)
        } ?: placeholderRes?.let { imageView.setImageResource(it) }

        return scope.launch {
            // Disk cache
            if (thumbFile.exists()) {
                val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(thumbFile.absolutePath) }
                bmp?.let {
                    memoryCache.put(key, it)
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == key) imageView.setImageBitmap(it)
                    }
                }
                return@launch
            }

            // Generate thumbnail
            val bmp = when (type.lowercase()) {
                "pdf" -> loadPdfThumbnail(context, uri)
                "cbz" -> loadCbzThumbnail(context, uri)
                "cbr" -> loadCbrThumbnail(context, uri)
                else -> null
            } ?: return@launch

            // Save to disk safely
            try { FileOutputStream(thumbFile).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos) } } catch (_: Exception) {}

            memoryCache.put(key, bmp)

            // Update ImageView if still valid
            withContext(Dispatchers.Main) {
                if (imageView.tag == key) {
                    // Optional fade-in
                    imageView.alpha = 0f
                    imageView.setImageBitmap(bmp)
                    imageView.animate().alpha(1f).setDuration(200).start()
                }
            }
        }
    }

    // Cleanup old thumbnails
    private fun cleanupOldThumbnails(cacheDir: File?, maxAgeDays: Int) {
        if (cleanupDone || cacheDir == null || !cacheDir.exists()) return

        val now = System.currentTimeMillis()
        val limit = maxAgeDays * 24L * 60L * 60L * 1000L

        cacheDir.listFiles()?.forEach { file ->
            if (file.name.endsWith("_thumb.jpg")) {
                if (now - file.lastModified() > limit) file.delete()
            }
        }

        cleanupDone = true
    }

    // PDF thumbnail (SAFE RENDER)
    private fun loadPdfThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val width = 400
                        val height = (400f / page.width * page.height).toInt()
                        val bmp = createBitmap(width, height)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // CBZ (ZIP) streaming thumbnail (NO TEMP FILES)
    private fun loadCbzThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zis = ZipInputStream(BufferedInputStream(input))
                var entry = zis.nextEntry

                while (entry != null) {
                    if (!entry.isDirectory &&
                        entry.name.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)$"))
                    ) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 3 }
                        return BitmapFactory.decodeStream(zis, null, opts)
                    }
                    entry = zis.nextEntry
                }
                null
            }
        } catch (_: Exception) { null }
    }

    // CBR (RAR) Streaming thumbnail - SEVENZIPJBINDING
    private fun loadCbrThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val channel = FileInputStream(pfd.fileDescriptor).channel
            val inStream = MappedFileInStream(channel)

            SevenZip.initSevenZipFromPlatformJAR()
            val archive = SevenZip.openInArchive(null, inStream)

            // Find the earliest image file path alphabetically
            val idx = (0 until archive.numberOfItems)
                .filter { i ->
                    val isFolder = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                    if (isFolder) return@filter false

                    val path = archive.getProperty(i, PropID.PATH)?.toString()?.lowercase() ?: ""
                    path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                            path.endsWith(".png") || path.endsWith(".webp")
                }
                .minByOrNull {
                    archive.getProperty(it, PropID.PATH).toString()
                }
                ?: return null

            // Stream into ByteArrayOutputStream ( reasonable size )
            val baos = ByteArrayOutputStream(2 * 1024 * 1024) // 2MB initial capacity

            archive.extractSlow(idx) { data ->
                baos.write(data)
                data.size
            }

            val bytes = baos.toByteArray()
            val opts = BitmapFactory.Options().apply { inSampleSize = 3 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

            archive.close()
            inStream.close()
            pfd.close()

            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}















