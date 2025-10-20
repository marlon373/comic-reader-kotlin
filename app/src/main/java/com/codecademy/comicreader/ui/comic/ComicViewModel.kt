package com.codecademy.comicreader.ui.comic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ComicViewModel : ViewModel() {

    private val _noComicsMessage = MutableStateFlow("No comics found")
    val noComicsMessage: StateFlow<String> = _noComicsMessage.asStateFlow()

    private val _addOnLibraryMessage = MutableStateFlow("Add on Library")
    val addOnLibraryMessage: StateFlow<String> = _addOnLibraryMessage.asStateFlow()

    private val _noComicsFolderMessage = MutableStateFlow("No comic folder found")
    val noComicsFolderMessage: StateFlow<String> = _noComicsFolderMessage.asStateFlow()
}

