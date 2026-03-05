package com.kreativekoala.echonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val favorites: StateFlow<List<Recording>> = combine(
        recordingRepository.getFavoriteRecordings(),
        _searchQuery
    ) { recordings, query ->
        if (query.isBlank()) recordings
        else recordings.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun toggleFavorite(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.updateRecording(recording.copy(isFavorite = !recording.isFavorite))
        }
    }
}
