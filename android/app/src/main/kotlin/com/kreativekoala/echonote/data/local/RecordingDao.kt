package com.kreativekoala.echonote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kreativekoala.echonote.data.model.Recording
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY dateCreated DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isFavorite = 1 ORDER BY dateCreated DESC")
    fun getFavoriteRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE folderId = :folderId ORDER BY dateCreated DESC")
    fun getRecordingsByFolder(folderId: String): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :query || '%' ORDER BY dateCreated DESC")
    fun searchRecordings(query: String): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording)

    @Update
    suspend fun update(recording: Recording)

    @Delete
    suspend fun delete(recording: Recording)

    @Query("DELETE FROM recordings WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM recordings WHERE folderId = :folderId")
    fun getRecordingCountForFolder(folderId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration), 0) FROM recordings WHERE folderId = :folderId")
    fun getTotalDurationForFolder(folderId: String): Flow<Long>
}
