package com.codecademy.comicreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.codecademy.comicreader.data.dao.ComicDao
import com.codecademy.comicreader.model.Comic

@Database(entities = [Comic::class], version = 1)
abstract class ComicDatabase : RoomDatabase() {

    abstract fun comicDao(): ComicDao

    companion object {
        @Volatile
        private var instance: ComicDatabase? = null

        fun getInstance(context: Context): ComicDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComicDatabase::class.java,
                    "comic_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

