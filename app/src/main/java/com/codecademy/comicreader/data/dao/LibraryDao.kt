package com.codecademy.comicreader.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codecademy.comicreader.model.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    // Reactive query — emits updates when folders table changes
    @Query("SELECT * FROM Folder")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM Folder WHERE path = :folderPath LIMIT 1")
    suspend fun getFolderByPath(folderPath: String): Folder?
}
