package com.kreativekoala.echonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {
    val deletedRecordings = repository.getDeletedRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(r: Recording) = viewModelScope.launch { repository.restoreRecording(r) }
    fun permanentlyDelete(r: Recording) = viewModelScope.launch { repository.permanentlyDelete(r) }
    fun emptyTrash() = viewModelScope.launch { repository.emptyTrash() }
}
