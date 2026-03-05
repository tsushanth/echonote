package com.kreativekoala.echonote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recording_folders")
data class RecordingFolder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val iconName: String = "folder",
    val colorHex: String = "FF3B4F"
)
