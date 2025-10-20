package com.codecademy.comicreader.ui.recent

import androidx.lifecycle.ViewModel
import com.codecademy.comicreader.model.Comic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update


class RecentViewModel : ViewModel() {

    // Use StateFlow instead of LiveData
    private val _recentComics = MutableStateFlow<List<Comic>>(emptyList())
    val recentComics: StateFlow<List<Comic>> = _recentComics

    /** Add a comic to the recent list (no duplicates, keep newest first, limit to 20). */
    fun addComicToRecent(comic: Comic) {
        _recentComics.update { currentList ->
            val newList = currentList.toMutableList().apply {
                removeAll { it.path == comic.path } // Avoid duplicates
                add(0, comic) // Add to top
            }
            if (newList.size > 20) newList.subList(0, 20) else newList
        }
    }

    /** Replace the entire recent list */
    fun setRecentComics(comics: List<Comic>) {
        _recentComics.value = comics.take(20) // Limit to 20 just in case
    }
}