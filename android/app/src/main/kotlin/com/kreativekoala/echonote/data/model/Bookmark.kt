package com.kreativekoala.echonote.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId")]
)
data class Bookmark(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long, // milliseconds into the recording
    val note: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val recordingId: String
) {
    val formattedTimestamp: String
        get() {
            val totalSeconds = timestamp / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
