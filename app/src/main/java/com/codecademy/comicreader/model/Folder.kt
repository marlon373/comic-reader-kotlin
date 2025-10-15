package com.codecademy.comicreader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entity class representing a Folder or Folder in the database
@Entity(tableName = "Folder")
class Folder(// Setter for Name
    // Getter for Name
     var name: String, // Setter for Path
    // Getter for Path
     var path: String, isFolder: Boolean
) {
    // Setter for ID
    // Getter for ID
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    // Getter for checking if it's a folder
    val isFolder: Boolean

    // Constructor to initialize a Folder object
    init {
        this.path = path
        this.isFolder = isFolder
    }
}
