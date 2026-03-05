package com.kreativekoala.echonote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kreativekoala.echonote.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    fun getBookmarksForRecording(recordingId: String): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteAllForRecording(recordingId: String)
}
