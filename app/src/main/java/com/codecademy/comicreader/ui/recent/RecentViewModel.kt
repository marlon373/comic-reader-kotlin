package com.codecademy.comicreader.ui.recent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.codecademy.comicreader.model.Comic
import java.util.Objects

class RecentViewModel : ViewModel() {
    private val recentComics = MutableLiveData<MutableList<Comic>>(ArrayList())

    fun getRecentComics(): LiveData<MutableList<Comic>> {
        return recentComics
    }

    fun addComicToRecent(comic: Comic) {
        var currentList: MutableList<Comic> =
            ArrayList(Objects.requireNonNull<MutableList<Comic>>(recentComics.getValue()))
        currentList.removeIf { c: Comic -> c.path == comic.path }  // Avoid duplicates
        currentList.add(0, comic) // Add to top

        // Limit recent list to 20
        if (currentList.size > 20) {
            currentList = currentList.subList(0, 20)
        }

        recentComics.value = currentList
    }

    fun setRecentComics(comics: MutableList<Comic>) {
        recentComics.value = comics
    }
}