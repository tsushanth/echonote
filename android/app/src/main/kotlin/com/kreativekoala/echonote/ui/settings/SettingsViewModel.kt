package com.kreativekoala.echonote.ui.settings

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.PremiumManager
import com.kreativekoala.echonote.util.Constants
import com.kreativekoala.echonote.util.TimeFormatting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val premiumManager: PremiumManager,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    val defaultFormat: StateFlow<AudioFormat> = settingsRepository.defaultFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioFormat.COMPRESSED)

    val defaultQuality: StateFlow<RecordingQuality> = settingsRepository.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingQuality.HIGH)

    val stereoDefault: StateFlow<Boolean> = settingsRepository.stereoDefault
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoLocation: StateFlow<Boolean> = settingsRepository.autoLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val transcriptionLanguage: StateFlow<TranscriptionLanguage> = settingsRepository.transcriptionLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptionLanguage.ENGLISH)

    val autoCopyTranscript: StateFlow<Boolean> = settingsRepository.autoCopyTranscript
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoDeleteAudioAfterTranscription: StateFlow<Boolean> = settingsRepository.autoDeleteAudioAfterTranscription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val soundEffectsEnabled: StateFlow<Boolean> = settingsRepository.soundEffectsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticFeedbackEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val contributeVoiceData: StateFlow<Boolean> = settingsRepository.contributeVoiceData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _storageUsed = MutableStateFlow(context.getString(R.string.settings_calculating))
    val storageUsed: StateFlow<String> = _storageUsed

    private val _totalRecordingsCount = MutableStateFlow(0)
    val totalRecordingsCount: StateFlow<Int> = _totalRecordingsCount

    private val _totalDuration = MutableStateFlow("00:00:00")
    val totalDuration: StateFlow<String> = _totalDuration

    private val _availableSpace = MutableStateFlow("")
    val availableSpace: StateFlow<String> = _availableSpace

    init {
        calculateStorageUsed()
    }

    fun setFormat(format: AudioFormat) {
        viewModelScope.launch { settingsRepository.setDefaultFormat(format) }
    }

    fun setQuality(quality: RecordingQuality) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(quality) }
    }

    fun setStereo(stereo: Boolean) {
        viewModelScope.launch { settingsRepository.setStereoDefault(stereo) }
    }

    fun setAutoLocation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoLocation(enabled) }
    }

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        viewModelScope.launch { settingsRepository.setTranscriptionLanguage(language) }
    }

    fun setAutoCopyTranscript(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCopyTranscript(enabled) }
    }

    fun setAutoDeleteAudioAfterTranscription(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDeleteAudioAfterTranscription(enabled) }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEffectsEnabled(enabled) }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticFeedbackEnabled(enabled) }
    }

    fun setContributeVoiceData(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setContributeVoiceData(enabled) }
    }

    private fun calculateStorageUsed() {
        viewModelScope.launch(Dispatchers.IO) {
            val recordingsDir = File(context.filesDir, Constants.RECORDINGS_DIRECTORY)
            if (!recordingsDir.exists()) {
                _storageUsed.value = context.getString(R.string.settings_zero_storage)
                _totalRecordingsCount.value = 0
                _totalDuration.value = "00:00:00"
            } else {
                val totalBytes = recordingsDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                _storageUsed.value = TimeFormatting.formatFileSize(totalBytes)
            }

            // Total count + total duration from Room
            val recordings = recordingRepository.getAllRecordingsOnce()
            _totalRecordingsCount.value = recordings.size
            val totalMs = recordings.sumOf { it.duration }
            val h = totalMs / 3_600_000
            val m = (totalMs % 3_600_000) / 60_000
            val s = (totalMs % 60_000) / 1_000
            _totalDuration.value = "%02d:%02d:%02d".format(h, m, s)

            // Available space from StatFs
            try {
                val stat = StatFs(context.filesDir.absolutePath)
                val available = stat.availableBlocksLong * stat.blockSizeLong
                _availableSpace.value = TimeFormatting.formatFileSize(available)
            } catch (_: Exception) {
                _availableSpace.value = ""
            }
        }
    }
}
