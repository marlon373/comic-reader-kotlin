package com.codecademy.comicreader.ui.comic


import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.net.toUri
import androidx.core.content.edit
import kotlinx.coroutines.Job


class ComicAdapter(
    private val comicList: MutableList<Comic>,
    private val listener: (Comic) -> Unit,
    isGrid: Boolean,
    private val context: Context
) : RecyclerView.Adapter<ComicAdapter.ViewHolder>() {

    // public mutable property so fragment can flip it without recreating adapter
    @Volatile
    var isGridView: Boolean = isGrid

    // Keep track of thumbnail loading jobs per ImageView (weak refs to avoid leaks)
    private val thumbnailJobs = mutableMapOf<Int, Job>() // keyed by viewId hashCode

    override fun getItemViewType(position: Int): Int {
        // Use viewType to signal which layout to inflate in onCreateViewHolder
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.comic_grid_view_display else R.layout.comic_list_view_display
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = comicList[position]
        holder.bind(item, listener)

        // Cancel previous thumbnail job for this holder's image (if any)
        val key = holder.ivComicRead.hashCode()
        thumbnailJobs[key]?.cancel()
        thumbnailJobs.remove(key)

        // Clear image
        holder.ivComicRead.setImageDrawable(null)

        // Start thumbnail job
        val job = ThumbnailManager.loadThumbnailAsync(
            context,
            item.path.toUri(),
            item.format,
            holder.ivComicRead)

        thumbnailJobs[key] = job

        holder.ivComicMenu.setOnClickListener {
            showPopupMenu(holder, item)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        val key = holder.ivComicRead.hashCode()
        thumbnailJobs[key]?.cancel()
        thumbnailJobs.remove(key)
        holder.ivComicRead.setImageDrawable(null)
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivComicRead: ImageView = itemView.findViewById(R.id.iv_comic_read)
        val ivComicMenu: ImageView = itemView.findViewById(R.id.iv_comic_menu)
        private val tvComicTitle: TextView = itemView.findViewById(R.id.tv_comic_title)
        private val tvComicDate: TextView = itemView.findViewById(R.id.tv_comic_date)
        private val tvComicSize: TextView = itemView.findViewById(R.id.tv_comic_size)
        private val tvComicFormat: TextView = itemView.findViewById(R.id.tv_comic_format)

        init {
            // HARD SAFETY — prevent theme tint/background from ever touching thumbnail
            ivComicRead.apply {
                background = null
                imageTintList = null
                setWillNotDraw(false)
            }
        }

        fun bind(item: Comic, listener: (Comic) -> Unit) {
            tvComicTitle.text = item.name
            tvComicDate.text = item.date
            tvComicSize.text = item.size
            tvComicFormat.text = item.format

            ivComicRead.setOnClickListener { listener(item) }
        }
    }

    // Popup Menu
    private fun showPopupMenu(holder: ViewHolder, item: Comic) {
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
            showAsDropDown(holder.ivComicMenu)
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

    companion object {
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_LIST = 2

        // Helper: convert SAF/content URI to readable path
        fun getReadablePath(context: Context, uri: Uri): String {
            return try {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val volume = parts[0]
                    val path = parts[1]
                    if (volume == "primary") "/storage/emulated/0/$path"
                    else "/storage/$volume/$path"
                } else uri.toString()
            } catch (_: Exception) {
                try {
                    val file = DocumentFile.fromSingleUri(context, uri)
                    file?.name ?: uri.toString()
                } catch (_: Exception) {
                    uri.toString()
                }
            }
        }
    }
}








