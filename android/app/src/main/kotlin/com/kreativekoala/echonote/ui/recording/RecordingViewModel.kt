package com.kreativekoala.echonote.ui.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.AudioRecorderService
import com.kreativekoala.echonote.service.LocationService
import com.kreativekoala.echonote.service.RecordingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val PREFS_REVIEW = "review_prefs"
private const val KEY_RECORDING_COUNT = "completed_recording_count"
private const val KEY_REVIEW_CARD_SHOWN = "review_prompt_shown"
private const val KEY_BATTERY_OPT_SHOWN = "battery_opt_shown"
private const val REVIEW_THRESHOLD = 3

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

    /** Emitted when startRecording() returns null (permission/hardware error). */
    private val _showPermissionError = MutableStateFlow(false)
    val showPermissionError: StateFlow<Boolean> = _showPermissionError

    /** Show a Xiaomi/MIUI battery optimization prompt. */
    private val _showBatteryOptPrompt = MutableStateFlow(false)
    val showBatteryOptPrompt: StateFlow<Boolean> = _showBatteryOptPrompt

    init {
        viewModelScope.launch {
            _selectedFormat.value = settingsRepository.defaultFormat.first()
            _selectedQuality.value = settingsRepository.defaultQuality.first()
            _isStereo.value = settingsRepository.stereoDefault.first()
            autoLocationEnabled = settingsRepository.autoLocation.first()
        }
        maybeShowBatteryOptPrompt()
    }

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog

    private val _recordingTitle = MutableStateFlow("")
    val recordingTitle: StateFlow<String> = _recordingTitle

    /** True if review card should be shown after saving. */
    private val _showReviewCard = MutableStateFlow(false)
    val showReviewCard: StateFlow<Boolean> = _showReviewCard

    private var pendingResult: AudioRecorderService.RecordingResult? = null
    private var pendingLocationName: String? = null

    fun startRecording() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "Recording_${dateFormat.format(Date())}"

        val defaultTitle = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
        _recordingTitle.value = defaultTitle

        val filePath = recorderService.startRecording(
            fileName = fileName,
            format = _selectedFormat.value,
            quality = _selectedQuality.value,
            isStereo = _isStereo.value,
            gain = _gain.value
        )

        if (filePath == null) {
            // Recording failed — likely a permission issue
            _showPermissionError.value = true
            return
        }

        // Start foreground service so recording survives screen lock
        val serviceIntent = RecordingForegroundService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        if (autoLocationEnabled) {
            viewModelScope.launch {
                pendingLocationName = locationService.getCurrentLocationName()
                if (pendingLocationName != null) {
                    _recordingTitle.value = pendingLocationName!!
                }
            }
        }
    }

    fun dismissPermissionError() {
        _showPermissionError.value = false
    }

    fun pauseRecording() = recorderService.pauseRecording()
    fun resumeRecording() = recorderService.resumeRecording()

    fun stopRecording() {
        pendingResult = recorderService.stopRecording()
        // Stop the foreground service
        context.stopService(RecordingForegroundService.startIntent(context))
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

            // Increment recording count and check if we should show the review card
            checkAndShowReviewCard()
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
        context.stopService(RecordingForegroundService.startIntent(context))
    }

    private val _gain = MutableStateFlow(1.0f)
    val gain: StateFlow<Float> = _gain

    val soundEffectsEnabled: StateFlow<Boolean> = settingsRepository.soundEffectsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticFeedbackEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setGain(v: Float) { _gain.value = v.coerceIn(0.5f, 2.0f) }
    fun setTitle(title: String) { _recordingTitle.value = title }
    fun setFormat(format: AudioFormat) { _selectedFormat.value = format }
    fun setQuality(quality: RecordingQuality) { _selectedQuality.value = quality }
    fun setStereo(stereo: Boolean) { _isStereo.value = stereo }

    fun dismissReviewCard() {
        _showReviewCard.value = false
        markReviewPromptShown()
    }

    fun dismissBatteryOptPrompt() {
        _showBatteryOptPrompt.value = false
        context.getSharedPreferences(PREFS_REVIEW, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BATTERY_OPT_SHOWN, true).apply()
    }

    fun openBatteryOptSettings() {
        dismissBatteryOptPrompt()
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    private fun checkAndShowReviewCard() {
        val prefs = context.getSharedPreferences(PREFS_REVIEW, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REVIEW_CARD_SHOWN, false)) return

        val count = prefs.getInt(KEY_RECORDING_COUNT, 0) + 1
        prefs.edit().putInt(KEY_RECORDING_COUNT, count).apply()

        if (count >= REVIEW_THRESHOLD) {
            _showReviewCard.value = true
        }
    }

    private fun markReviewPromptShown() {
        context.getSharedPreferences(PREFS_REVIEW, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REVIEW_CARD_SHOWN, true).apply()
    }

    private fun maybeShowBatteryOptPrompt() {
        val prefs = context.getSharedPreferences(PREFS_REVIEW, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_OPT_SHOWN, false)) return

        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        if (manufacturer == "xiaomi" || manufacturer == "redmi") {
            // Also check if we're already excluded from battery optimization
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                _showBatteryOptPrompt.value = true
            } else {
                // Already excluded, mark as shown so we don't ask again
                prefs.edit().putBoolean(KEY_BATTERY_OPT_SHOWN, true).apply()
            }
        }
    }
}
