package com.example.simplenote.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var title: String,
    var content: String,
    var lastEdited: Long = System.currentTimeMillis(),
    var isSynced: Boolean = false,
    var username: String = "",
    var isPinned: Boolean = false,
    var color: String = "orange",
    var userId: Long = 0,

)