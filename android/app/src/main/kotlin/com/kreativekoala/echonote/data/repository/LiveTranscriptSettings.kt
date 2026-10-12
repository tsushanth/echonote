package com.kreativekoala.echonote.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.liveDataStore by preferencesDataStore(name = "live_transcript")

/** The "Live transcript while recording" switch. Its own small store, so it can't collide with SettingsRepository. */
@Singleton
class LiveTranscriptSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = booleanPreferencesKey("enabled")

    /** Default OFF. A read error counts as OFF. */
    val enabled: Flow<Boolean> = context.liveDataStore.data
        .map { it[key] ?: false }
        .catch { emit(false) }

    suspend fun setEnabled(value: Boolean) {
        context.liveDataStore.edit { it[key] = value }
    }
}
