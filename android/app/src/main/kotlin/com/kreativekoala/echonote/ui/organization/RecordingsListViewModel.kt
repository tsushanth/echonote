package com.kreativekoala.echonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.service.PremiumManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption { DATE, NAME, DURATION, SIZE }

@HiltViewModel
class RecordingsListViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val premiumManager: PremiumManager
) : ViewModel() {

    val isPremium = premiumManager.isPremium

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOption = MutableStateFlow(SortOption.DATE)
    val sortOption: StateFlow<SortOption> = _sortOption

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

    val recordings: StateFlow<List<Recording>> = combine(
        recordingRepository.getAllRecordings(),
        _searchQuery,
        _sortOption
    ) { recordings, query, sort ->
        val filtered = if (query.isBlank()) recordings
        else recordings.filter { it.title.contains(query, ignoreCase = true) }

        when (sort) {
            SortOption.DATE -> filtered.sortedByDescending { it.dateCreated }
            SortOption.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOption.DURATION -> filtered.sortedByDescending { it.duration }
            SortOption.SIZE -> filtered.sortedByDescending { it.fileSize }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOption(option: SortOption) { _sortOption.value = option }

    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() {
        viewModelScope.launch {
            recordingRepository.deleteRecordingsByIds(_selectedIds.value.toList())
            clearSelection()
        }
    }

    fun toggleFavorite(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.updateRecording(recording.copy(isFavorite = !recording.isFavorite))
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun renameRecording(recording: Recording, newTitle: String) {
        viewModelScope.launch {
            recordingRepository.updateRecording(
                recording.copy(title = newTitle, dateModified = System.currentTimeMillis())
            )
        }
    }

    fun canCreateRecording(): Boolean {
        return premiumManager.canCreateRecording(recordings.value.size)
    }
}
