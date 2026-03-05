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
}
