package com.codecademy.comicreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.codecademy.comicreader.data.dao.LibraryDao
import com.codecademy.comicreader.model.Folder
import kotlin.concurrent.Volatile

// Database class for handling database operations using Room
@Database(entities = [Folder::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {

    // Access DAO methods
    abstract fun folderItemDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: LibraryDatabase? = null

        // Singleton to ensure only one instance of the database is created
        fun getInstance(context: Context): LibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "library_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
