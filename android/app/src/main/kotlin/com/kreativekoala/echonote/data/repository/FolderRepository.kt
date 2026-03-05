package com.kreativekoala.echonote.data.repository

import com.kreativekoala.echonote.data.local.RecordingFolderDao
import com.kreativekoala.echonote.data.model.RecordingFolder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: RecordingFolderDao
) {
    fun getAllFolders(): Flow<List<RecordingFolder>> = folderDao.getAllFolders()

    suspend fun getFolderById(id: String): RecordingFolder? = folderDao.getFolderById(id)

    suspend fun insertFolder(folder: RecordingFolder) = folderDao.insert(folder)

    suspend fun updateFolder(folder: RecordingFolder) = folderDao.update(folder)

    suspend fun deleteFolder(folder: RecordingFolder) = folderDao.delete(folder)
}
