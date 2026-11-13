package com.kreativekoala.echonote.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.TranscriptionEngine
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.DeviceMemory
import com.kreativekoala.echonote.service.LanguageDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State behind the first-run language card and the language line above "Transcribe this recording". */
@HiltViewModel
class LanguageChoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : ViewModel() {

    val language: StateFlow<TranscriptionLanguage> = settings.transcriptionLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageDefaults.forDevice(context))

    val engine: StateFlow<TranscriptionEngine> = settings.transcriptionEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptionEngine.FAST)

    /** Set once the user picks a language from the card, so the card stays up long enough to show the Accurate hint for it. */
    private val engaged = MutableStateFlow(false)

    /**
     * Whether to show the first-run card: not yet confirmed or skipped, and the user has not already chosen a language
     * (an existing user who did, in Settings, never sees it).
     */
    val showCard: StateFlow<Boolean> = combine(settings.languagePromptDone, settings.hasStoredTranscriptionLanguage, engaged) { done, stored, engaged ->
        !done && (!stored || engaged)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalMemBytes: Long by lazy { DeviceMemory.totalBytes(context) }

    fun choose(language: TranscriptionLanguage) {
        engaged.value = true
        viewModelScope.launch { settings.setTranscriptionLanguage(language) }
    }

    /** "Looks right" or skip: keep the shown language as the user's choice and hide the card for good. */
    fun confirm() {
        val current = language.value
        viewModelScope.launch { settings.confirmTranscriptionLanguage(current) }
    }

    /** The one-tap opt-in from the hint; the larger download itself still happens on first transcription. */
    fun useAccurate() {
        viewModelScope.launch { settings.setTranscriptionEngine(TranscriptionEngine.ACCURATE) }
    }
}
