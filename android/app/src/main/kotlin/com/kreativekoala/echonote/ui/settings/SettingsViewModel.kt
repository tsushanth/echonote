package com.kreativekoala.echonote.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val premiumManager: PremiumManager
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

    private val _storageUsed = MutableStateFlow(context.getString(R.string.settings_calculating))
    val storageUsed: StateFlow<String> = _storageUsed

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

    private fun calculateStorageUsed() {
        viewModelScope.launch(Dispatchers.IO) {
            val recordingsDir = File(context.filesDir, Constants.RECORDINGS_DIRECTORY)
            if (!recordingsDir.exists()) {
                _storageUsed.value = context.getString(R.string.settings_zero_storage)
                return@launch
            }
            val totalBytes = recordingsDir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
            _storageUsed.value = TimeFormatting.formatFileSize(totalBytes)
        }
    }
}
