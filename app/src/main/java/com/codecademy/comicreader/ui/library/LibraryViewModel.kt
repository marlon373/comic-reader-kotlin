package com.codecademy.comicreader.ui.library

import androidx.lifecycle.ViewModel
import com.codecademy.comicreader.model.Folder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class LibraryViewModel : ViewModel() {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _addFolderMessage = MutableStateFlow(
        "Use the “+” button to add the folder\ncontaining the .cbz or .cbr files"
    )
    val addFolderMessage: StateFlow<String> = _addFolderMessage.asStateFlow()

    // Folder add/remove one-time events
    private val _folderAdded = MutableSharedFlow<Unit>()
    val folderAdded: SharedFlow<Unit> = _folderAdded.asSharedFlow()

    private val _folderRemoved = MutableSharedFlow<Unit>()
    val folderRemoved: SharedFlow<Unit> = _folderRemoved.asSharedFlow()

    fun setFolders(folders: List<Folder>) {
        _folders.value = folders.toList()
    }

    suspend fun notifyFolderAdded() {
        _folderAdded.emit(Unit)
    }

    suspend fun notifyFolderRemoved() {
        _folderRemoved.emit(Unit)
    }
}