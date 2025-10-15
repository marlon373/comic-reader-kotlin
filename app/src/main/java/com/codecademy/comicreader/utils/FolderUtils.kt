package com.codecademy.comicreader.utils

import android.content.Context
import com.codecademy.comicreader.model.Folder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import androidx.core.content.edit

object FolderUtils {
    private const val PREFS_NAME = "folders"
    private const val KEY_FOLDER_LIST = "folder_list"

    private val gson = Gson()
    private val folderListType: Type = object : TypeToken<MutableList<Folder?>?>() {}.type

    // Save folders
    fun saveFolders(context: Context, folders: MutableList<Folder>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(folders)
        prefs.edit { putString(KEY_FOLDER_LIST, json) }
    }

    // Load folders
    fun loadFolders(context: Context): MutableList<Folder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FOLDER_LIST, null)
        if (json == null || json.isEmpty()) return ArrayList()

        try {
            return gson.fromJson(json, folderListType)
        } catch (e: Exception) {
            e.printStackTrace()
            return ArrayList()
        }
    }
}
