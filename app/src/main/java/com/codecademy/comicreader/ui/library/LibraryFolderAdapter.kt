package com.codecademy.comicreader.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codecademy.comicreader.R
import com.codecademy.comicreader.model.Folder

class LibraryFolderAdapter(
    private val comicsList: List<Folder>,
    private val listener: (Folder) -> Unit,
    private val longClickListener: (Folder, View) -> Unit
) : RecyclerView.Adapter<LibraryFolderAdapter.ViewHolder>() {

    private var selectedFolder: Folder? = null

    // Sets the selected folder and refreshes the UI
    fun setSelectedFolder(folder: Folder?) {
        selectedFolder = folder
        notifyDataSetChanged() // Refresh selection
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_library, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = comicsList[position]

        // Ensure correct background state is applied
        holder.itemView.setBackgroundResource(R.drawable.folder_selector)
        holder.itemView.isActivated = item == selectedFolder
        holder.bind(item, listener, longClickListener)
    }

    override fun getItemCount() = comicsList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivLibraryIcon: ImageView = itemView.findViewById(R.id.iv_library_icon)
        private val tvLibraryName: TextView = itemView.findViewById(R.id.tv_library_name)

        // Binds folder data to the UI elements and sets up click listeners
        fun bind(item: Folder, listener: (Folder) -> Unit, longClickListener: (Folder, View) -> Unit) {
            tvLibraryName.text = item.name
            ivLibraryIcon.setImageResource(
                if (item.isFolder) R.drawable.ic_folder_library
                else R.drawable.ic_file_library
            )

            itemView.setOnClickListener { listener(item) }
            itemView.setOnLongClickListener {
                longClickListener(item, itemView)
                true
            }
        }
    }
}



