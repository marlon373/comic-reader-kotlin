package com.codecademy.comicreader.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codecademy.comicreader.model.Comic
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {

    // Reactive query — automatically emits updates when data changes
    @Query("SELECT * FROM Comics")
    fun getAllComics(): Flow<List<Comic>>

    // Marked suspend for coroutine safety
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comics: List<Comic>)

    @Query("DELETE FROM Comics")
    suspend fun deleteAll()

    @Query("DELETE FROM Comics WHERE path = :comicPath")
    suspend fun deleteComicByPath(comicPath: String)

    @Query("DELETE FROM Comics WHERE path LIKE :folderPath || '%'")
    suspend fun deleteComicsByFolderPath(folderPath: String)
}
