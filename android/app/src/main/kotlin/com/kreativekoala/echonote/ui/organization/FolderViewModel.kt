package com.kreativekoala.echonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kreativekoala.echonote.data.model.RecordingFolder
import com.kreativekoala.echonote.data.repository.FolderRepository
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.service.PremiumManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FolderViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordingRepository: RecordingRepository,
    private val premiumManager: PremiumManager
) : ViewModel() {

    val isPremium = premiumManager.isPremium

    val folders: StateFlow<List<RecordingFolder>> = folderRepository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // For folder detail view
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId

    private val _selectedFolderName = MutableStateFlow("")
    val selectedFolderName: StateFlow<String> = _selectedFolderName

    val folderRecordings: StateFlow<List<Recording>> = _selectedFolderId
        .flatMapLatest { folderId ->
            if (folderId != null) recordingRepository.getRecordingsByFolder(folderId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFolder(folder: RecordingFolder) {
        _selectedFolderId.value = folder.id
        _selectedFolderName.value = folder.name
    }

    fun clearSelectedFolder() {
        _selectedFolderId.value = null
        _selectedFolderName.value = ""
    }

    fun canCreateFolder(): Boolean {
        return premiumManager.canCreateFolder(folders.value.size)
    }

    fun createFolder(name: String, iconName: String = "folder", colorHex: String = "FF3B4F") {
        viewModelScope.launch {
            folderRepository.insertFolder(
                RecordingFolder(name = name, iconName = iconName, colorHex = colorHex)
            )
        }
    }

    fun renameFolder(folder: RecordingFolder, newName: String) {
        viewModelScope.launch {
            folderRepository.updateFolder(folder.copy(name = newName))
        }
    }

    fun deleteFolder(folder: RecordingFolder) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folder)
        }
    }

    fun moveRecordingToFolder(recordingId: String, folderId: String?) {
        viewModelScope.launch {
            val recording = recordingRepository.getRecordingById(recordingId) ?: return@launch
            recordingRepository.updateRecording(recording.copy(folderId = folderId))
        }
    }

    fun toggleFavorite(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.updateRecording(recording.copy(isFavorite = !recording.isFavorite))
        }
    }

    fun getRecordingCount(folderId: String): StateFlow<Int> =
        recordingRepository.getRecordingCountForFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun getTotalDuration(folderId: String): StateFlow<Long> =
        recordingRepository.getTotalDurationForFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
}
