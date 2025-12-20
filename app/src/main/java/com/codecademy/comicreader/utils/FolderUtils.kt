package com.codecademy.comicreader.utils

import android.content.Context
import com.codecademy.comicreader.model.Folder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import androidx.core.content.edit

/**
 * Utility class for saving and loading Folder objects
 * using SharedPreferences + Gson.
 *
 * This is the Java equivalent of the Kotlin `object FolderUtils`.
 */
object FolderUtils {
    // SharedPreferences file name
    private const val PREFS_NAME = "folders"
    // Key used to store the folder list JSON
    private const val KEY_FOLDER_LIST = "folder_list"

    // Gson instance for JSON serialization
    private val gson = Gson()
    /**
     * Type token for:
     * MutableList<Folder>
     *
     * Required because of Java type erasure.
     */
    private val  FOLDER_LIST_TYPE: Type = object : TypeToken<MutableList<Folder?>?>() {}.type

    /**
     * Saves a list of folders into SharedPreferences as JSON.
     *
     * @param context Android context
     * @param folders Mutable list of Folder objects
     */
    fun saveFolders(context: Context, folders: MutableList<Folder>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Convert folder list to JSON
        val json = gson.toJson(folders)
        // Save JSON string
        prefs.edit { putString(KEY_FOLDER_LIST, json) }
    }

    /**
     * Loads the folder list from SharedPreferences.
     *
     * @param context Android context
     * @return Mutable list of Folder objects (never null)
     */
    fun loadFolders(context: Context): MutableList<Folder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FOLDER_LIST, null)
        // No saved data → return empty list
        if (json == null || json.isEmpty()) return ArrayList()

        try {
            // Safety check (Gson may return null)
            return gson.fromJson(json,  FOLDER_LIST_TYPE)
        } catch (e: Exception) {
            // Corrupted JSON or schema mismatch
            e.printStackTrace()
            return ArrayList()
        }
    }
}
