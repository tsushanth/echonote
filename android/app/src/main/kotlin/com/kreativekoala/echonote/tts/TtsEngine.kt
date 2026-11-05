package com.kreativekoala.echonote.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Engine-agnostic interface for on-device text-to-speech.
 *
 * The only implementation today is [SystemTtsEngine], which wraps Android's TextToSpeech but ALWAYS picks the engine
 * explicitly (never the phone's default, which may be a network or third-party engine) and never uses a network voice.
 * There is deliberately no server fallback: audio and transcripts never leave the phone. A later opt-in server voice
 * would be a separate engine behind this interface and needs its own explicit consent screen.
 */
interface TtsEngine {

    val state: StateFlow<TtsState>

    /** On-device voices for the prepared language (empty until [prepare] completes). */
    val voices: StateFlow<List<TtsVoice>>

    /** Index (into the list given to [speakQueue]) of the item being spoken, or -1 when nothing is. */
    val spokenIndex: StateFlow<Int>

    /** The engine package chosen by [prepare], or null when none could be used. */
    val engine: StateFlow<TtsEngineInfo?>

    /** Choose an engine that can speak [locale] with an installed offline voice. Idempotent per language. */
    suspend fun prepare(locale: Locale = Locale.getDefault())

    /**
     * Speak [items] one after another starting at [startIndex]; each item is one utterance (a sentence, a key point).
     * [spokenIndex] follows the item being spoken, which is what lets the UI highlight it and lets pause/resume and
     * skip work at item granularity. Returns once speech is queued; observe [state] for the rest.
     */
    suspend fun speakQueue(
        items: List<String>,
        startIndex: Int = 0,
        voiceId: String? = null,
        speed: Float = 1.0f,
        locale: Locale = Locale.getDefault(),
    )

    /** Convenience: split [text] into sentences and speak them. */
    suspend fun speak(text: String, voiceId: String? = null, speed: Float = 1.0f, locale: Locale = Locale.getDefault()) =
        speakQueue(SentenceSplitter.split(text), 0, voiceId, speed, locale)

    /** Stops speaking but remembers the current item; [resume] continues from the start of that item. */
    fun pause()
    fun resume()

    /** Jump to [index] in the current queue (while speaking or paused). */
    fun skipTo(index: Int)

    /** Changes the speaking rate; takes effect immediately by restarting the current item. */
    fun setSpeed(speed: Float)

    fun stop()

    /** Tear down resources. */
    fun release()
}

sealed class TtsState {
    data object Idle : TtsState()
    data object Preparing : TtsState()
    data object Ready : TtsState()
    data class Speaking(val progressFraction: Float) : TtsState()
    data object Paused : TtsState()
    /** The last item was spoken to the end (as opposed to [Idle], which means stopped). */
    data object Finished : TtsState()
    /** No engine can speak this language offline; the UI must say why and never fall back to a server. */
    data class Unavailable(val reason: TtsUnavailable) : TtsState()
    data class Failed(val message: String) : TtsState()
}

sealed class TtsUnavailable {
    /** No text-to-speech engine is installed at all. */
    data object NoEngine : TtsUnavailable()
    /** An engine exists but has no offline voice installed for the language; [enginePackage] can be asked to install it. */
    data class MissingVoiceData(val enginePackage: String?, val languageTag: String) : TtsUnavailable()
    /** No installed engine supports the language. */
    data class LanguageNotSupported(val languageTag: String) : TtsUnavailable()
    /** Engines are installed but none would start. */
    data object InitFailed : TtsUnavailable()
}

data class TtsEngineInfo(val packageName: String, val label: String)

data class TtsVoice(
    val id: String,
    val displayName: String,
    val locale: String,
    val isFemale: Boolean? = null,
    val isOnDevice: Boolean = true,
)
