package com.kreativekoala.echonote.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val STEREO_DEFAULT = booleanPreferencesKey("stereo_default")
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")
        val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
        val AUTO_COPY_TRANSCRIPT = booleanPreferencesKey("auto_copy_transcript")
        val AUTO_DELETE_AUDIO = booleanPreferencesKey("auto_delete_audio")
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    }

    val defaultFormat: Flow<AudioFormat> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_FORMAT]?.let { AudioFormat.valueOf(it) } ?: AudioFormat.COMPRESSED
    }

    val defaultQuality: Flow<RecordingQuality> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_QUALITY]?.let { RecordingQuality.valueOf(it) } ?: RecordingQuality.HIGH
    }

    val stereoDefault: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.STEREO_DEFAULT] ?: false
    }

    val autoLocation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_LOCATION] ?: true
    }

    val transcriptionLanguage: Flow<TranscriptionLanguage> = context.dataStore.data.map { prefs ->
        prefs[Keys.TRANSCRIPTION_LANGUAGE]?.let {
            runCatching { TranscriptionLanguage.valueOf(it) }.getOrNull()
        } ?: TranscriptionLanguage.ENGLISH
    }

    val autoCopyTranscript: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_COPY_TRANSCRIPT] ?: false
    }

    val autoDeleteAudioAfterTranscription: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_DELETE_AUDIO] ?: false
    }

    val soundEffectsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SOUND_EFFECTS] ?: true
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAPTIC_FEEDBACK] ?: true
    }

    suspend fun setDefaultFormat(format: AudioFormat) {
        context.dataStore.edit { it[Keys.DEFAULT_FORMAT] = format.name }
    }

    suspend fun setDefaultQuality(quality: RecordingQuality) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.name }
    }

    suspend fun setStereoDefault(stereo: Boolean) {
        context.dataStore.edit { it[Keys.STEREO_DEFAULT] = stereo }
    }

    suspend fun setAutoLocation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LOCATION] = enabled }
    }

    suspend fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        context.dataStore.edit { it[Keys.TRANSCRIPTION_LANGUAGE] = language.name }
    }

    suspend fun setAutoCopyTranscript(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_COPY_TRANSCRIPT] = enabled }
    }

    suspend fun setAutoDeleteAudioAfterTranscription(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DELETE_AUDIO] = enabled }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_EFFECTS] = enabled }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }
}
