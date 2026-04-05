package com.kreativekoala.echonote.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.AudioRecorderService
import com.kreativekoala.echonote.service.LocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recorderService: AudioRecorderService,
    private val locationService: LocationService,
    private val recordingRepository: RecordingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isRecording = recorderService.isRecording
    val isPaused = recorderService.isPaused
    val currentAmplitude = recorderService.currentAmplitude
    val elapsedTimeMs = recorderService.elapsedTimeMs
    val meterLevels = recorderService.meterLevels

    private val _selectedFormat = MutableStateFlow(AudioFormat.COMPRESSED)
    val selectedFormat: StateFlow<AudioFormat> = _selectedFormat

    private val _selectedQuality = MutableStateFlow(RecordingQuality.HIGH)
    val selectedQuality: StateFlow<RecordingQuality> = _selectedQuality

    private val _isStereo = MutableStateFlow(false)
    val isStereo: StateFlow<Boolean> = _isStereo

    private var autoLocationEnabled = true

    init {
        viewModelScope.launch {
            _selectedFormat.value = settingsRepository.defaultFormat.first()
            _selectedQuality.value = settingsRepository.defaultQuality.first()
            _isStereo.value = settingsRepository.stereoDefault.first()
            autoLocationEnabled = settingsRepository.autoLocation.first()
        }
    }

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog

    private val _recordingTitle = MutableStateFlow("")
    val recordingTitle: StateFlow<String> = _recordingTitle

    private var pendingResult: AudioRecorderService.RecordingResult? = null
    private var pendingLocationName: String? = null

    fun startRecording() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "Recording_${dateFormat.format(Date())}"

        val defaultTitle = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
        _recordingTitle.value = defaultTitle

        recorderService.startRecording(
            fileName = fileName,
            format = _selectedFormat.value,
            quality = _selectedQuality.value,
            isStereo = _isStereo.value
        )

        if (autoLocationEnabled) {
            viewModelScope.launch {
                pendingLocationName = locationService.getCurrentLocationName()
                if (pendingLocationName != null) {
                    _recordingTitle.value = pendingLocationName!!
                }
            }
        }
    }

    fun pauseRecording() = recorderService.pauseRecording()
    fun resumeRecording() = recorderService.resumeRecording()

    fun stopRecording() {
        pendingResult = recorderService.stopRecording()
        if (pendingResult != null) {
            _showSaveDialog.value = true
        }
    }

    fun saveRecording() {
        val result = pendingResult ?: return
        val title = _recordingTitle.value.ifBlank { context.getString(R.string.recording_new_default_title) }

        viewModelScope.launch {
            val recording = Recording(
                title = title,
                fileUri = result.filePath,
                duration = result.durationMs,
                fileSize = result.fileSize,
                audioFormat = _selectedFormat.value,
                quality = _selectedQuality.value,
                isStereo = _isStereo.value,
                locationName = pendingLocationName
            )
            recordingRepository.insertRecording(recording)
            _showSaveDialog.value = false
            pendingResult = null
            pendingLocationName = null
        }
    }

    fun discardRecording() {
        pendingResult?.let { result ->
            java.io.File(result.filePath).delete()
        }
        pendingResult = null
        pendingLocationName = null
        _showSaveDialog.value = false
    }

    fun cancelRecording() {
        recorderService.cancelRecording()
    }

    fun setTitle(title: String) { _recordingTitle.value = title }
    fun setFormat(format: AudioFormat) { _selectedFormat.value = format }
    fun setQuality(quality: RecordingQuality) { _selectedQuality.value = quality }
    fun setStereo(stereo: Boolean) { _isStereo.value = stereo }
}
