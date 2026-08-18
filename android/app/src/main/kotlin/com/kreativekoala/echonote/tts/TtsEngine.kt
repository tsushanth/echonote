package com.kreativekoala.echonote.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic interface for on-device text-to-speech.
 *
 * Phase A implementation uses Android's system TextToSpeech (always on-device on
 * supported devices, zero new dependencies). Phase B will swap in Sherpa-ONNX +
 * Kokoro for consistent voice quality across devices, without changing any UI.
 *
 * Mirrors the SecureVox Whisper STT pattern: a single-purpose engine wrapper with
 * StateFlow-based progress, state, and lifecycle owned by a singleton manager.
 */
interface TtsEngine {

    val state: StateFlow<TtsState>

    /** Available voices (may be empty until [prepare] completes). */
    val voices: StateFlow<List<TtsVoice>>

    /** Prepare the engine: load any models, query system voices, etc. Idempotent. */
    suspend fun prepare()

    /**
     * Speak the supplied text. Implementations control playback themselves
     * (via system TTS, AudioTrack, etc.). Returns when speech has started; use
     * [state] to observe playback progress and completion.
     *
     * @param voiceId Engine-specific voice identifier from [voices], or null for default.
     */
    suspend fun speak(text: String, voiceId: String? = null, speed: Float = 1.0f)

    /** Pause / resume / stop are best-effort — not all engines support all three. */
    fun pause()
    fun resume()
    fun stop()

    /** Tear down resources. Call from Application.onTerminate or when feature is disabled. */
    fun release()
}

sealed class TtsState {
    data object Idle : TtsState()
    data object Preparing : TtsState()
    data object Ready : TtsState()
    data class Speaking(val progressFraction: Float) : TtsState()
    data object Paused : TtsState()
    data class Failed(val message: String) : TtsState()
}

data class TtsVoice(
    val id: String,
    val displayName: String,
    val locale: String,
    val isFemale: Boolean? = null,
    val isOnDevice: Boolean = true,
)
