package com.codecademy.comicreader.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.codecademy.comicreader.model.Folder

class LibraryViewModel : ViewModel() {

    val addFolderLibrary: MutableLiveData<String> = MutableLiveData()
    val folderAdded: MutableLiveData<Boolean> = MutableLiveData(false)
    private val foldersLiveData: MutableLiveData<List<Folder>> = MutableLiveData()
    private val folderRemoved: MutableLiveData<Boolean> = MutableLiveData(false)

    init {
        addFolderLibrary.value = "Use the “ +” button to add the folder\n" +
                "   containing the .cbz or cbr files"
        foldersLiveData.value = emptyList()
    }

    fun getAddFolderLibrary(): LiveData<String> {
        return addFolderLibrary
    }

    fun getFolders(): LiveData<List<Folder>> {
        return foldersLiveData
    }

    fun setFolders(folders: List<Folder>) {
        foldersLiveData.postValue(ArrayList(folders)) // Ensure LiveData updates UI
    }

    fun getFolderAdded(): LiveData<Boolean> {
        return folderAdded
    }

    fun notifyFolderAdded() {
        folderAdded.value = true // Notifies ComicFragment
    }

    fun resetFolderAddedFlag() {
        folderAdded.value = false // Prevents repeated triggers
    }

    fun notifyFolderRemoved() {
        folderRemoved.value = true
    }

    fun notifyFolderRemovedHandled() {
        folderRemoved.value = false
    }

    fun getFolderRemoved(): LiveData<Boolean> {
        return folderRemoved
    }
}