package com.kreativekoala.echonote.ui.playback

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.service.AudioEditorService
import com.kreativekoala.echonote.service.AudioPlayerService
import com.kreativekoala.echonote.service.PremiumManager
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
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerService: AudioPlayerService,
    private val recordingRepository: RecordingRepository,
    private val transcriptionService: TranscriptionService,
    private val audioEditorService: AudioEditorService,
    private val premiumManager: PremiumManager
) : ViewModel() {

    val isPremium = premiumManager.isPremium
    val isPlaying = playerService.isPlaying
    val currentPositionMs = playerService.currentPositionMs
    val durationMs = playerService.durationMs
    val playbackSpeed = playerService.playbackSpeed
    val skipSilence = playerService.skipSilence

    private val _currentRecording = MutableStateFlow<Recording?>(null)
    val currentRecording: StateFlow<Recording?> = _currentRecording

    private val _waveformSamples = MutableStateFlow(FloatArray(0))
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples

    private val _showPlayback = MutableStateFlow(false)
    val showPlayback: StateFlow<Boolean> = _showPlayback

    // Transcription state
    val isTranscribing = transcriptionService.isTranscribing
    val transcriptionProgress = transcriptionService.progress
    val transcriptionStatus = transcriptionService.statusMessage

    private val _transcriptionResult = MutableStateFlow<String?>(null)
    val transcriptionResult: StateFlow<String?> = _transcriptionResult

    private val _transcriptionError = MutableStateFlow<String?>(null)
    val transcriptionError: StateFlow<String?> = _transcriptionError

    // Trim state
    private val _isTrimming = MutableStateFlow(false)
    val isTrimming: StateFlow<Boolean> = _isTrimming

    private val _trimStart = MutableStateFlow(0f)
    val trimStart: StateFlow<Float> = _trimStart

    private val _trimEnd = MutableStateFlow(1f)
    val trimEnd: StateFlow<Float> = _trimEnd

    private val _showTrimMode = MutableStateFlow(false)
    val showTrimMode: StateFlow<Boolean> = _showTrimMode

    fun loadRecording(recording: Recording) {
        _currentRecording.value = recording
        _showPlayback.value = true
        _transcriptionResult.value = recording.transcript
        _showTrimMode.value = false
        _trimStart.value = 0f
        _trimEnd.value = 1f
        playerService.play(recording.fileUri)

        viewModelScope.launch(Dispatchers.IO) {
            val waveform = playerService.generateWaveformData(recording.fileUri)
            _waveformSamples.value = waveform
        }
    }

    fun togglePlayPause() = playerService.togglePlayPause()

    fun seekTo(positionMs: Long) = playerService.seekTo(positionMs)

    fun seekToProgress(progress: Float) = playerService.seekToProgress(progress)

    fun skipForward() = playerService.skipForward()

    fun skipBackward() = playerService.skipBackward()

    fun increaseSpeed() {
        playerService.setPlaybackSpeed(playerService.playbackSpeed.value + 0.25f)
    }

    fun decreaseSpeed() {
        playerService.setPlaybackSpeed(playerService.playbackSpeed.value - 0.25f)
    }

    fun toggleSkipSilence() {
        playerService.setSkipSilence(!playerService.skipSilence.value)
    }

    fun stop() {
        playerService.stop()
        _showPlayback.value = false
    }

    fun dismiss() {
        playerService.release()
        _showPlayback.value = false
        _currentRecording.value = null
        _waveformSamples.value = FloatArray(0)
        _transcriptionResult.value = null
        _transcriptionError.value = null
        _showTrimMode.value = false
    }

    // Transcription
    fun refreshPremiumStatus() {
        premiumManager.refreshPremiumStatus()
    }

    fun transcribe() {
        val rec = _currentRecording.value ?: return
        if (transcriptionService.isTranscribing.value) return

        _transcriptionError.value = null

        viewModelScope.launch(Dispatchers.Main) {
            when (val result = transcriptionService.transcribe(rec.fileUri)) {
                is TranscriptionResult.Success -> {
                    _transcriptionResult.value = result.text
                    _transcriptionError.value = null
                    recordingRepository.updateRecording(rec.copy(transcript = result.text))
                    _currentRecording.value = rec.copy(transcript = result.text)
                }
                is TranscriptionResult.Error -> {
                    _transcriptionError.value = result.message
                }
            }
        }
    }

    fun clearTranscriptionError() {
        _transcriptionError.value = null
    }

    fun isTranscriptionAvailable(): Boolean = transcriptionService.isAvailable()

    // Trim
    fun toggleTrimMode() {
        _showTrimMode.value = !_showTrimMode.value
        if (_showTrimMode.value) {
            _trimStart.value = 0f
            _trimEnd.value = 1f
        }
    }

    fun setTrimRange(start: Float, end: Float) {
        val safeStart = start.coerceIn(0f, 1f)
        val safeEnd = end.coerceIn(0f, 1f)
        if (safeStart < safeEnd) {
            _trimStart.value = safeStart
            _trimEnd.value = safeEnd
        }
    }

    fun performTrim() {
        val rec = _currentRecording.value ?: return
        val duration = durationMs.value
        if (duration <= 0) return

        _isTrimming.value = true

        viewModelScope.launch {
            val startMs = (_trimStart.value * duration).toLong()
            val endMs = (_trimEnd.value * duration).toLong()

            val inputFile = File(rec.fileUri)
            val ext = inputFile.extension
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_trimmed.$ext"
            )

            // Trim on IO thread
            val success = withContext(Dispatchers.IO) {
                audioEditorService.trimAudio(
                    inputUri = rec.fileUri,
                    outputUri = outputFile.absolutePath,
                    startTimeMs = startMs,
                    endTimeMs = endMs
                )
            }

            if (success) {
                // Stop playback on main thread (ExoPlayer requirement)
                playerService.stop()

                // File operations on IO thread
                withContext(Dispatchers.IO) {
                    inputFile.delete()
                    outputFile.renameTo(inputFile)
                }

                // Update recording metadata
                val newDuration = endMs - startMs
                val newSize = withContext(Dispatchers.IO) { inputFile.length() }
                val updated = rec.copy(
                    duration = newDuration,
                    fileSize = newSize,
                    dateModified = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    recordingRepository.updateRecording(updated)
                }
                _currentRecording.value = updated

                // Reload on main thread, waveform generation on IO
                playerService.play(rec.fileUri)
                val waveform = withContext(Dispatchers.IO) {
                    playerService.generateWaveformData(rec.fileUri)
                }
                _waveformSamples.value = waveform
            }

            _isTrimming.value = false
            _showTrimMode.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerService.release()
    }
}
