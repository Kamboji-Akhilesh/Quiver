package com.kamboji.quiver.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deletion_history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val fileName: String,
    val trashPath: String?, // null if permanently deleted
    val deletedAt: Long = System.currentTimeMillis(),
    val canRestore: Boolean = true
)

