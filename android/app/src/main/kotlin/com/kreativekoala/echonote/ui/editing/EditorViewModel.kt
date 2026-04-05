package com.kreativekoala.echonote.ui.editing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.service.AudioEditorService
import com.kreativekoala.echonote.service.TranscriptionResult
import com.kreativekoala.echonote.service.TranscriptionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editorService: AudioEditorService,
    private val transcriptionService: TranscriptionService,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs

    val isTranscribing = transcriptionService.isTranscribing

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _transcriptionError = MutableStateFlow<String?>(null)
    val transcriptionError: StateFlow<String?> = _transcriptionError

    fun loadRecording(recording: Recording) {
        _recording.value = recording
        _trimStartMs.value = 0L
        _trimEndMs.value = recording.duration
    }

    fun setTrimRange(startMs: Long, endMs: Long) {
        _trimStartMs.value = startMs
        _trimEndMs.value = endMs
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearTranscriptionError() {
        _transcriptionError.value = null
    }

    fun trimRecording() {
        val rec = _recording.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val inputFile = File(rec.fileUri)
            val ext = inputFile.extension
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_trimmed.$ext"
            )

            val success = withContext(Dispatchers.IO) {
                editorService.trimAudio(
                    inputUri = rec.fileUri,
                    outputUri = outputFile.absolutePath,
                    startTimeMs = _trimStartMs.value,
                    endTimeMs = _trimEndMs.value
                )
            }

            if (success) {
                withContext(Dispatchers.IO) {
                    inputFile.delete()
                    outputFile.renameTo(inputFile)
                }

                val newDuration = _trimEndMs.value - _trimStartMs.value
                val newSize = withContext(Dispatchers.IO) { inputFile.length() }
                val updated = rec.copy(
                    duration = newDuration,
                    fileSize = newSize,
                    dateModified = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    recordingRepository.updateRecording(updated)
                }
                _recording.value = updated
                _trimStartMs.value = 0L
                _trimEndMs.value = newDuration
            } else {
                _errorMessage.value = context.getString(R.string.editor_error_trim_failed)
            }

            _isProcessing.value = false
        }
    }

    fun enhanceRecording() {
        val rec = _recording.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true

            val inputFile = File(rec.fileUri)
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_enhanced.m4a"
            )

            val success = withContext(Dispatchers.IO) {
                editorService.enhanceAudio(
                    inputUri = rec.fileUri,
                    outputUri = outputFile.absolutePath
                )
            }

            if (success) {
                withContext(Dispatchers.IO) {
                    inputFile.delete()
                    outputFile.renameTo(inputFile)
                }

                val newSize = withContext(Dispatchers.IO) { inputFile.length() }
                val updated = rec.copy(
                    isEnhanced = true,
                    fileSize = newSize,
                    dateModified = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    recordingRepository.updateRecording(updated)
                }
                _recording.value = updated
            } else {
                _errorMessage.value = context.getString(R.string.editor_error_enhance_failed)
            }

            _isProcessing.value = false
        }
    }

    fun separateVocals() {
        val rec = _recording.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true

            val inputFile = File(rec.fileUri)
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_vocals.m4a"
            )

            val success = withContext(Dispatchers.IO) {
                editorService.separateVocals(
                    inputUri = rec.fileUri,
                    outputUri = outputFile.absolutePath
                )
            }

            if (success) {
                val newDuration = withContext(Dispatchers.IO) {
                    editorService.getAudioDuration(outputFile.absolutePath)
                }
                val newSize = withContext(Dispatchers.IO) { outputFile.length() }

                val vocalsRecording = rec.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = context.getString(R.string.editor_vocals_suffix, rec.title),
                    fileUri = outputFile.absolutePath,
                    duration = newDuration,
                    fileSize = newSize,
                    hasVocalLayer = true,
                    dateCreated = System.currentTimeMillis(),
                    dateModified = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    recordingRepository.insertRecording(vocalsRecording)
                }
            } else {
                _errorMessage.value = context.getString(R.string.editor_error_vocals_failed)
            }

            _isProcessing.value = false
        }
    }

    fun transcribeRecording() {
        val rec = _recording.value ?: return
        _transcriptionError.value = null
        viewModelScope.launch {
            when (val result = transcriptionService.transcribe(rec.fileUri)) {
                is TranscriptionResult.Success -> {
                    val updated = rec.copy(transcript = result.text)
                    recordingRepository.updateRecording(updated)
                    _recording.value = updated
                }
                is TranscriptionResult.Error -> {
                    _transcriptionError.value = result.message
                }
            }
        }
    }
}
