package com.codecademy.comicreader.ui.comic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ComicViewModel : ViewModel() {
    val noComicsMessage: MutableLiveData<String> = MutableLiveData()
    val addOnLibraryMessage: MutableLiveData<String> = MutableLiveData()
    val noComicsFolderMessage: MutableLiveData<String> = MutableLiveData()

    init {
        noComicsMessage.value = "No comics found"
        noComicsFolderMessage.value = "No comic folder found"
        addOnLibraryMessage.value = "Add on Library"
    }

    fun getNoComicsMessage(): LiveData<String?> {
        return noComicsMessage
    }

    fun getAddOnLibraryMessage(): LiveData<String> {
        return addOnLibraryMessage
    }

    val noComicFolderMessage: LiveData<String>
        get() = noComicsFolderMessage
}
