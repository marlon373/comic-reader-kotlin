package com.codecademy.comicreader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Comics")
data class Comic(
    val name: String,
    @PrimaryKey val path: String,
    val date: String,
    val size: String,
    val format: String
)

