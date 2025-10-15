package com.codecademy.comicreader.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codecademy.comicreader.model.Comic

@Dao
interface ComicDao {

    // Get all comics in the database
    @Query("SELECT * FROM Comics")
    fun getAllComics(): List<Comic>

    // Insert or replace multiple comics (avoids duplicates)
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    fun insertAll(comics: List<Comic>)

    // Delete all comics
    @Query("DELETE FROM Comics")
    fun deleteAll()

    // Delete a single comic by its exact path
    @Query("DELETE FROM Comics WHERE path = :comicPath")
    fun deleteComicByPath(comicPath: String)

    // Delete all comics from a folder (matches prefix)
    @Query("DELETE FROM Comics WHERE path LIKE :folderPath || '%'")
    fun deleteComicsByFolderPath(folderPath: String)
}