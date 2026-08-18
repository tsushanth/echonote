package com.kreativekoala.echonote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kreativekoala.echonote.data.model.Bookmark
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.RecordingFolder

@Database(
    entities = [Recording::class, RecordingFolder::class, Bookmark::class],
    version = 3,
    exportSchema = false
)
abstract class EchoNoteDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun recordingFolderDao(): RecordingFolderDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN transcriptSegmentsJson TEXT")
            }
        }

        fun create(context: Context): EchoNoteDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EchoNoteDatabase::class.java,
                "echonote.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
