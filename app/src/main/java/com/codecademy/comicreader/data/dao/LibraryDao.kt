package com.codecademy.comicreader.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.codecademy.comicreader.model.Folder

@Dao // DAO (Data Access Object) interface for interacting with the database
interface LibraryDao {
    // Inserts a new folder into the database
    @Insert
    fun insert(folder: Folder?)

    // Deletes a specific folder
    @Delete
    fun delete(folder: Folder?)

    @get:Query("SELECT * FROM Folder")
    val allFolders: MutableList<Folder>

    // Retrieves a folder by its path (if it exists)
    @Query("SELECT * FROM Folder WHERE path = :folderPath LIMIT 1")
    fun getFolderByPath(folderPath: String?): Folder?
}