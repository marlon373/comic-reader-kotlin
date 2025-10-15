package com.codecademy.comicreader.ui.comic

import androidx.recyclerview.widget.DiffUtil
import com.codecademy.comicreader.model.Comic

class ComicDiffCallback(
    private val oldList: List<Comic>,
    private val newList: List<Comic>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].path == newList[newItemPosition].path
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}