package com.codecademy.comicreader.ui.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.codecademy.comicreader.R
import com.codecademy.comicreader.dialog.InfoDialog
import com.codecademy.comicreader.dialog.RemoveFileDialog
import com.codecademy.comicreader.model.Comic
import com.github.junrar.Archive
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ComicAdapter(
    private val comicList: MutableList<Comic>,       // list of comics displayed
    private val listener: (Comic) -> Unit,           // click callback for "read" button
    private val isGridView: Boolean,                 // grid or list mode
    context: Context,
    sharedDispatcher: CoroutineDispatcher
) : RecyclerView.Adapter<ComicAdapter.ViewHolder>() {

    companion object {
        // Use 1/4 of the app’s max heap for thumbnails
        private val MAX_CACHE_SIZE = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()

        // In-memory cache of thumbnails (key = filePath)
        private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024 // size in KB
            }
        }

        // Thread pool for background work (IO, decoding)
        // Replace ExecutorService with CoroutineDispatcher
        private var dispatcher: CoroutineDispatcher? = null

        // Ensure disk cache cleanup only runs once per app launch
        private var cleanupDone = false

        fun clearMemoryCache() {
            memoryCache.evictAll()
        }

        // Convert content:// or SAF Uri to a readable path string
        fun getReadablePath(context: Context, uri: Uri): String {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val volume = parts[0]
                    val path = parts[1]
                    return if (volume == "primary") {
                        "/storage/emulated/0/$path"
                    } else {
                        "/storage/$volume/$path"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val file = DocumentFile.fromSingleUri(context, uri)
                if (file?.name != null) return file.name!!
            } catch (_: Exception) {}
            return uri.toString()
        }

        // Delete old thumbnails from cacheDir (default = older than 30 days)
        fun cleanupOldThumbnails(context: Context, maxAgeDays: Int = 30) {
            try {
                val cacheDir = context.cacheDir
                val now = System.currentTimeMillis()
                val maxAgeMillis = maxAgeDays * 24 * 60 * 60 * 1000L // 30 days

                cacheDir.listFiles { file ->
                    file.name.startsWith("thumb_") && file.name.endsWith(".jpg")
                }?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMillis) {
                        file.delete()
                    }
                }

                Log.d("ComicAdapter", "Thumbnail cache cleanup finished.")
            } catch (e: Exception) {
                Log.e("ComicAdapter", "Failed to clean thumbnails", e)
            }
        }
    }

    init {
        // Ensure background executor is available
        if (dispatcher == null) {
            dispatcher = sharedDispatcher
        }
        // Run disk cache cleanup once per app launch
        if (!cleanupDone) {
            cleanupOldThumbnails(context.applicationContext)
            cleanupDone = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGridView) R.layout.comic_grid_view_display else R.layout.comic_list_view_display
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = comicList[position]
        val context = holder.itemView.context

        // Skip missing/removed files
        val file = DocumentFile.fromSingleUri(context, item.path.toUri())
        if (file == null || !file.exists()) {
            Log.w("ComicAdapter", "Skipping missing comic: ${item.path}")
            return
        }

        // Popup menu (Remove + Info)
        holder.ibtnComicMenu.setOnClickListener {
            val popupView = LayoutInflater.from(context).inflate(R.layout.custom_popup_menu, null)
            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                elevation = 10f
                isOutsideTouchable = true
                isFocusable = true
                showAsDropDown(holder.ibtnComicMenu)
            }

            val tvComicRemove: TextView = popupView.findViewById(R.id.tv_Pop_Menu_Remove)
            val tvComicInfo: TextView = popupView.findViewById(R.id.tv_Pop_Menu_Info)

            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            // Remove option
            tvComicRemove.setOnClickListener {
                val dialog = RemoveFileDialog.newInstance {
                    val comicToRemove = comicList[currentPosition]
                    val prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
                    val removedPaths = prefs.getStringSet("removed_paths", HashSet())?.toMutableSet() ?: mutableSetOf()
                    removedPaths.add(comicToRemove.path)
                    prefs.edit { putStringSet("removed_paths", removedPaths) }

                    comicList.removeAt(currentPosition)
                    notifyItemRemoved(currentPosition)
                }

                (context as? AppCompatActivity)?.let { activity ->
                    dialog.show(activity.supportFragmentManager, "removeComicDialog")
                }
                popupWindow.dismiss()
            }

            // Info option
            tvComicInfo.setOnClickListener {
                val readablePath = getReadablePath(context, item.path.toUri())
                val dialog = InfoDialog.newInstance(item.name, readablePath, item.date, item.size)
                (context as? AppCompatActivity)?.let { activity ->
                    dialog.show(activity.supportFragmentManager, "infoDialog")
                }
                popupWindow.dismiss()
            }
        }

        // Bind data + load thumbnail
        holder.bind(item, listener)
    }

    override fun getItemCount() = comicList.size

    // Replace entire comic list
    fun updateComicList(newComics: List<Comic>) {
        comicList.clear()
        comicList.addAll(newComics)
        notifyDataSetChanged()
    }

    // Append new comics incrementally
    fun appendComics(newComics: List<Comic>) {
        val start = comicList.size
        comicList.addAll(newComics)
        notifyItemRangeInserted(start, newComics.size)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ibtnComicRead: ImageButton = itemView.findViewById(R.id.ibtn_comic_read)
        val ibtnComicMenu: ImageButton = itemView.findViewById(R.id.ibtn_comic_menu)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_comic_title)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_comic_date)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_comic_size)
        private val tvFormat: TextView = itemView.findViewById(R.id.tv_comic_format)

        fun bind(item: Comic, listener: (Comic) -> Unit) {
            // Set comic metadata
            tvTitle.text = item.name
            tvDate.text = item.date
            tvSize.text = item.size
            tvFormat.text = item.format

            ibtnComicRead.tag = item.path
            ibtnComicRead.setImageDrawable(null)

            // Use coroutine Flow for thumbnails
            val context = itemView.context
            (context as? LifecycleOwner)?.lifecycleScope?.launch {
                generateThumbnailFlow(item.path, ibtnComicRead).collect { bmp ->
                    if (item.path == ibtnComicRead.tag) {
                        ibtnComicRead.setImageBitmap(bmp)
                    }
                }
            }

            // Click → open reader
            ibtnComicRead.setOnClickListener { listener(item) }
        }


        // Thumbnail Flow
        fun generateThumbnailFlow(filePath: String, imageView: ImageView): Flow<Bitmap> = flow {
            val context = imageView.context
            val cacheKey = filePath
            val thumbFile = File(context.cacheDir, "thumb_${cacheKey.hashCode()}.jpg")

            // Memory cache hit
            memoryCache[cacheKey]?.let {
                if (!it.isRecycled) {
                    emit(it)
                    return@flow
                }
            }

            // Disk cache hit
            if (thumbFile.exists()) {
                val bmp = BitmapFactory.decodeFile(thumbFile.path)
                bmp?.let {
                    memoryCache.put(cacheKey, it)
                    emit(it)
                    return@flow
                }
            }

            // Generate new thumbnail
            val bmp = withContext(dispatcher!!) {
                //  Load thumbnail depending on format
                when {
                    filePath.endsWith(".cbz", true) -> generateCbzThumbnail(context, filePath, thumbFile)
                    filePath.endsWith(".cbr", true) -> generateCbrThumbnail(context, filePath, thumbFile)
                    filePath.endsWith(".pdf", true) -> generatePdfThumbnail(context, filePath, thumbFile)
                    else -> null
                }
            }

            bmp?.let {
                memoryCache.put(cacheKey, it)
                emit(it)
            }
        }.flowOn(dispatcher!!)


        // Thumbnail Generators
        private fun generateCbzThumbnail(context: Context, filePath: String, thumbFile: File): Bitmap? {
            var firstImageName: String? = null
            // First pass → find first image name
            context.contentResolver.openInputStream(filePath.toUri())?.use { is1 ->
                ZipInputStream(is1).use { zis ->
                    val images = mutableListOf<String>()
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (!entry!!.isDirectory && entry.name.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)$"))) {
                            images.add(entry.name)
                        }
                    }
                    images.sort()
                    firstImageName = images.firstOrNull()
                }
            }
            // Second pass → extract and downscale
            firstImageName ?: return null
            var bmp: Bitmap? = null
            context.contentResolver.openInputStream(filePath.toUri())?.use { is2 ->
                ZipInputStream(is2).use { zis2 ->
                    var entry: ZipEntry?
                    while (zis2.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == firstImageName) {
                            val bytes = zis2.readBytes()
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            decoded?.let {
                                val scaled = it.scale(400, (400f / it.width * it.height).roundToInt())
                                FileOutputStream(thumbFile).use { fos ->
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                                }
                                bmp = scaled
                            }
                            break
                        }
                    }
                }
            }
            return bmp
        }

        private fun generateCbrThumbnail(context: Context, filePath: String, thumbFile: File): Bitmap? {
            // Copy .cbr file to cache because junrar needs a File
            val tempFile = File(context.cacheDir, "temp_${filePath.hashCode()}.cbr")
            context.contentResolver.openInputStream(filePath.toUri())?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            val archive = Archive(tempFile)
            val entries = archive.fileHeaders
                .filter { !it.isDirectory && it.fileName.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)$")) }
                .sortedBy { it.fileName }

            if (entries.isEmpty()) return null
            val first = entries.first()
            // Extract first image to temp file
            val tempImg = File(context.cacheDir, "cbr_img_${filePath.hashCode()}.jpg")
            FileOutputStream(tempImg).use { fos -> archive.extractFile(first, fos) }
            // Decode + downscale
            val bmp = BitmapFactory.decodeFile(tempImg.path)
            var scaled: Bitmap? = null
            bmp?.let {
                scaled = it.scale(400, (400f / it.width * it.height).roundToInt())
                FileOutputStream(thumbFile).use { fos -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                it.recycle()
            }
            tempImg.delete()
            tempFile.delete()
            return scaled
        }

        private fun generatePdfThumbnail(context: Context, filePath: String, thumbFile: File): Bitmap? {
            var bmp: Bitmap? = null
            context.contentResolver.openFileDescriptor(filePath.toUri(), "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val w = 400
                        val h = (400f / page.width * page.height).roundToInt()
                        val bitmap = createBitmap(w, h)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(thumbFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        }
                        bmp = bitmap
                    }
                }
            }
            return bmp
        }
    }
}






