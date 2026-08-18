package com.kreativekoala.echonote.data.repository

import com.kreativekoala.echonote.data.local.BookmarkDao
import com.kreativekoala.echonote.data.local.RecordingDao
import com.kreativekoala.echonote.data.model.Bookmark
import com.kreativekoala.echonote.data.model.Recording
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val bookmarkDao: BookmarkDao
) {
    fun getAllRecordings(): Flow<List<Recording>> = recordingDao.getAllRecordings()

    fun getFavoriteRecordings(): Flow<List<Recording>> = recordingDao.getFavoriteRecordings()

    fun getRecordingsByFolder(folderId: String): Flow<List<Recording>> =
        recordingDao.getRecordingsByFolder(folderId)

    fun searchRecordings(query: String): Flow<List<Recording>> =
        recordingDao.searchRecordings(query)

    suspend fun getRecordingById(id: String): Recording? = recordingDao.getRecordingById(id)

    suspend fun insertRecording(recording: Recording) = recordingDao.insert(recording)

    suspend fun updateRecording(recording: Recording) = recordingDao.update(recording)

    suspend fun deleteRecording(recording: Recording) {
        recordingDao.update(recording.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRecordingsByIds(ids: List<String>) {
        val now = System.currentTimeMillis()
        val recordings = ids.mapNotNull { recordingDao.getRecordingById(it) }
        recordings.forEach { rec ->
            recordingDao.update(rec.copy(deletedAt = now))
        }
    }

    fun getDeletedRecordings(): Flow<List<Recording>> = recordingDao.getDeletedRecordings()

    suspend fun restoreRecording(recording: Recording) {
        recordingDao.update(recording.copy(deletedAt = null))
    }

    suspend fun permanentlyDelete(recording: Recording) {
        try { File(recording.fileUri).delete() } catch (_: Exception) {}
        recordingDao.delete(recording)
    }

    suspend fun emptyTrash() {
        val deletedSnapshot = recordingDao.getDeletedRecordingsOnce()
        deletedSnapshot.forEach { rec ->
            try { File(rec.fileUri).delete() } catch (_: Exception) {}
        }
        recordingDao.purgeOldDeleted(Long.MAX_VALUE)
    }

    fun getBookmarksForRecording(recordingId: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForRecording(recordingId)

    suspend fun addBookmark(bookmark: Bookmark) = bookmarkDao.insert(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    suspend fun getAllRecordingsOnce(): List<Recording> = recordingDao.getAllRecordingsOnce()

    fun getRecordingCountForFolder(folderId: String): Flow<Int> =
        recordingDao.getRecordingCountForFolder(folderId)

    fun getTotalDurationForFolder(folderId: String): Flow<Long> =
        recordingDao.getTotalDurationForFolder(folderId)
}
