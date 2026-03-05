package com.kreativekoala.echonote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kreativekoala.echonote.data.model.RecordingFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingFolderDao {

    @Query("SELECT * FROM recording_folders ORDER BY dateCreated DESC")
    fun getAllFolders(): Flow<List<RecordingFolder>>

    @Query("SELECT * FROM recording_folders WHERE id = :id")
    suspend fun getFolderById(id: String): RecordingFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: RecordingFolder)

    @Update
    suspend fun update(folder: RecordingFolder)

    @Delete
    suspend fun delete(folder: RecordingFolder)
}
