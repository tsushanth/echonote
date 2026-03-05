package com.kreativekoala.echonote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kreativekoala.echonote.data.model.Bookmark
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.RecordingFolder

@Database(
    entities = [Recording::class, RecordingFolder::class, Bookmark::class],
    version = 1,
    exportSchema = false
)
abstract class EchoNoteDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun recordingFolderDao(): RecordingFolderDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): EchoNoteDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EchoNoteDatabase::class.java,
                "echonote.db"
            ).build()
        }
    }
}
