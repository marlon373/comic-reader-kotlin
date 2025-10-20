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
import java.util.zip.ZipInputStream
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComicAdapter(
    private val comicList: MutableList<Comic>,         // list of comics displayed
    private val listener: (Comic) -> Unit,             // click callback for "read" button
    private val isGridView: Boolean,                   // grid or list mode
    private val context: Context,
    private val sharedDispatcher: CoroutineDispatcher  // Coroutine dispatcher passed from Fragment
) : RecyclerView.Adapter<ComicAdapter.ViewHolder>() {

    companion object {
        private val MAX_CACHE_SIZE = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()

        private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

        private var cleanupDone = false
        

        // Convert SAF or content URI to readable path
        fun getReadablePath(context: Context, uri: Uri): String {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val volume = parts[0]
                    val path = parts[1]
                    return if (volume == "primary") "/storage/emulated/0/$path"
                    else "/storage/$volume/$path"
                }
            } catch (_: Exception) {}

            return try {
                val file = DocumentFile.fromSingleUri(context, uri)
                file?.name ?: uri.toString()
            } catch (_: Exception) { uri.toString() }
        }

        // Delete old thumbnails from cacheDir (older than maxAgeDays)
        fun cleanupOldThumbnails(context: Context, maxAgeDays: Int = 30) {
            if (cleanupDone) return
            try {
                val cacheDir = context.cacheDir
                val now = System.currentTimeMillis()
                val maxAgeMillis = maxAgeDays * 24 * 60 * 60 * 1000L
                cacheDir.listFiles { file -> file.name.startsWith("thumb_") && file.name.endsWith(".jpg") }
                    ?.forEach { file ->
                        if (now - file.lastModified() > maxAgeMillis) file.delete()
                    }
                cleanupDone = true
            } catch (e: Exception) {
                Log.e("ComicAdapter", "Failed to clean thumbnails", e)
            }
        }
    }

    init {
        cleanupOldThumbnails(context.applicationContext)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGridView) R.layout.comic_grid_view_display else R.layout.comic_list_view_display
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = comicList[position]
        val file = DocumentFile.fromSingleUri(context, item.path.toUri())

        if (file == null || !file.exists()) {
            Log.w("ComicAdapter", "Skipping missing comic: ${item.path}")
            return
        }

        // Popup menu
        holder.ibtnComicMenu.setOnClickListener {
            showPopupMenu(holder, item)
        }

        // Bind data + thumbnail
        holder.bind(item, listener)
    }

    override fun getItemCount() = comicList.size

    fun updateComicList(newComics: List<Comic>) {
        comicList.clear()
        comicList.addAll(newComics)
        notifyDataSetChanged()
    }

    fun appendComics(newComics: List<Comic>) {
        val start = comicList.size
        comicList.addAll(newComics)
        notifyItemRangeInserted(start, newComics.size)
    }


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ibtnComicRead: ImageButton = itemView.findViewById(R.id.ibtn_comic_read)
        val ibtnComicMenu: ImageButton = itemView.findViewById(R.id.ibtn_comic_menu)
        private val tvComicTitle: TextView = itemView.findViewById(R.id.tv_comic_title)
        private val tvComicDate: TextView = itemView.findViewById(R.id.tv_comic_date)
        private val tvComicSize: TextView = itemView.findViewById(R.id.tv_comic_size)
        private val tvComicFormat: TextView = itemView.findViewById(R.id.tv_comic_format)

        fun bind(item: Comic, listener: (Comic) -> Unit) {
            tvComicTitle.text = item.name
            tvComicDate.text = item.date
            tvComicSize.text = item.size
            tvComicFormat.text = item.format

            ibtnComicRead.setImageDrawable(null)
            ibtnComicRead.tag = item.path

            when (item.format.lowercase()) {
                "cbz" -> loadCbzThumbnailAsync(item.path, ibtnComicRead)
                "cbr" -> loadCbrThumbnailAsync(item.path, ibtnComicRead)
                "pdf" -> loadPdfThumbnailAsync(item.path, ibtnComicRead)
            }

            ibtnComicRead.setOnClickListener { listener(item) }
        }
    }

    //Popup Menu
    private fun showPopupMenu(holder: ViewHolder, item: Comic) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.custom_popup_menu, null)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 10f
            isOutsideTouchable = true
            isFocusable = true
            showAsDropDown(holder.ibtnComicMenu)
        }

        val tvComicRemove: TextView = popupView.findViewById(R.id.tv_Pop_Menu_Remove)
        val tvComicInfo: TextView = popupView.findViewById(R.id.tv_Pop_Menu_Info)
        val currentPosition = holder.bindingAdapterPosition
        if (currentPosition == RecyclerView.NO_POSITION) return

        tvComicRemove.setOnClickListener {
            RemoveFileDialog.newInstance {
                val comicToRemove = comicList[currentPosition]
                val prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
                val removedPaths = prefs.getStringSet("removed_paths", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                removedPaths.add(comicToRemove.path)
                prefs.edit { putStringSet("removed_paths", removedPaths) }
                comicList.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
            }.show((context as AppCompatActivity).supportFragmentManager, "removeComicDialog")
            popupWindow.dismiss()
        }

        tvComicInfo.setOnClickListener {
            val readablePath = getReadablePath(context, item.path.toUri())
            InfoDialog.newInstance(item.name, readablePath, item.date, item.size)
                .show((context as AppCompatActivity).supportFragmentManager, "infoDialog")
            popupWindow.dismiss()
        }
    }

    // Thumbnail helpers
    private fun useCachedOrGenerate(filePath: String, imageView: ImageView, generateThumb: (File) -> Unit) {
        val cacheKey = filePath
        val cached = memoryCache[cacheKey]
        if (cached != null && !cached.isRecycled) {
            if (filePath == imageView.tag) imageView.setImageBitmap(cached)
            return
        }

        val thumbFile = File(context.cacheDir, "thumb_${cacheKey.hashCode()}.jpg")
        if (thumbFile.exists()) {
            CoroutineScope(sharedDispatcher).launch {
                loadImageIntoView(imageView, cacheKey, Uri.fromFile(thumbFile))
            }
            return
        }

        CoroutineScope(sharedDispatcher).launch {
            try {
                generateThumb(thumbFile)
                withContext(Dispatchers.Main) {
                    if (filePath == imageView.tag) loadImageIntoView(imageView, cacheKey, Uri.fromFile(thumbFile))
                }
            } catch (e: Exception) {
                Log.e("ComicAdapter", "Thumbnail generation failed", e)
            }
        }
    }

    private fun loadCbzThumbnailAsync(filePath: String, imageView: ImageView) {
        useCachedOrGenerate(filePath, imageView) { thumbFile ->
            val context = imageView.context
            var firstImageName: String? = null

            context.contentResolver.openInputStream(filePath.toUri())?.use { input ->
                ZipInputStream(input).use { zis ->
                    generateSequence { zis.nextEntry }
                        .filter { !it.isDirectory && it.name.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)$")) }
                        .sortedBy { it.name }
                        .firstOrNull()?.let { firstImageName = it.name }
                }
            }

            if (firstImageName != null) {
                context.contentResolver.openInputStream(filePath.toUri())?.use { input ->
                    ZipInputStream(input).use { zis ->
                        generateSequence { zis.nextEntry }
                            .filter { it.name == firstImageName }
                            .firstOrNull()?.let {
                                val bytes = zis.readBytes()
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                bmp?.let {
                                    val scaled = it.scale(400, (400f / it.width * it.height).toInt())
                                    FileOutputStream(thumbFile).use { fos -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                                    it.recycle()
                                    scaled.recycle()
                                }
                            }
                    }
                }
            }
        }
    }

    private fun loadCbrThumbnailAsync(filePath: String, imageView: ImageView) {
        useCachedOrGenerate(filePath, imageView) { thumbFile ->
            val context = imageView.context
            val tempFile = File(context.cacheDir, "temp_${filePath.hashCode()}.cbr")
            context.contentResolver.openInputStream(filePath.toUri())?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            val archive = Archive(tempFile)
            val firstEntry = archive.fileHeaders
                .filter { !it.isDirectory && it.fileName.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)$")) }
                .minByOrNull { it.fileName }

            firstEntry?.let { it ->
                val tempImg = File(context.cacheDir, "cbr_img_${filePath.hashCode()}.jpg")
                FileOutputStream(tempImg).use { fos -> archive.extractFile(it, fos) }
                val bmp = BitmapFactory.decodeFile(tempImg.path)
                bmp?.let {
                    val scaled = it.scale(400, (400f / it.width * it.height).toInt())
                    FileOutputStream(thumbFile).use { fos -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                    it.recycle()
                    scaled.recycle()
                }
                tempImg.delete()
            }
            tempFile.delete()
        }
    }

    private fun loadPdfThumbnailAsync(filePath: String, imageView: ImageView) {
        useCachedOrGenerate(filePath, imageView) { thumbFile ->
            context.contentResolver.openFileDescriptor(filePath.toUri(), "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val targetWidth = 400
                        val targetHeight = (400f / page.width * page.height).toInt()
                        val bitmap = createBitmap(targetWidth, targetHeight)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(thumbFile).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun loadImageIntoView(imageView: ImageView, cacheKey: String, uri: Uri) {
        CoroutineScope(sharedDispatcher).launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    bitmap?.let {
                        withContext(Dispatchers.Main) {
                            if (cacheKey == imageView.tag) {
                                memoryCache.put(cacheKey, it)
                                imageView.setImageBitmap(it)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ComicAdapter", "Bitmap load failed", e)
            }
        }
    }
}






