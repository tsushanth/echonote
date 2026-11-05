package com.kreativekoala.echonote.search

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class VoiceUnavailable {
    /** The on-device recognizer API needs Android 13. Older phones may send audio to a server, which ClearVoice never does. */
    NeedsAndroid13,
    /** Android 13+, but the phone has no offline speech recognizer installed. */
    NoOnDeviceRecognizer,
}

enum class VoiceError { NoSpeech, LanguageUnavailable, NoPermission, Busy, Other }

interface VoiceListener {
    fun onListening()
    fun onPartial(text: String)
    fun onResult(text: String)
    fun onError(error: VoiceError)
}

/**
 * Speech to text for search, ON DEVICE ONLY. It uses [SpeechRecognizer.createOnDeviceSpeechRecognizer] (Android 13+),
 * which by contract never sends audio to a server. The default recognizer is never used because it may do exactly that
 * and would break "audio never leaves your phone". Call from the main thread.
 */
class VoiceQueryInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /** Null when voice search can be offered. */
    fun unavailable(): VoiceUnavailable? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> VoiceUnavailable.NeedsAndroid13
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> VoiceUnavailable.NoOnDeviceRecognizer
        else -> null
    }

    fun start(locale: Locale, listener: VoiceListener) {
        if (unavailable() != null) { listener.onError(VoiceError.Other); return }
        cancel()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { listener.onPartial(it) }
            }
            override fun onResults(results: Bundle?) {
                val text = first(results)
                if (text.isNullOrBlank()) listener.onError(VoiceError.NoSpeech) else listener.onResult(text.trim())
                cancel()
            }
            override fun onError(error: Int) {
                listener.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoSpeech
                        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.LanguageUnavailable
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NoPermission
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.Busy
                        else -> VoiceError.Other
                    }
                )
                cancel()
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    /** Stops listening and releases the recognizer. Safe to call any time. */
    fun cancel() {
        recognizer?.let {
            try { it.cancel(); it.destroy() } catch (_: Exception) { }
        }
        recognizer = null
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12 // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED (API 31)
        const val ERROR_LANGUAGE_UNAVAILABLE = 13 // SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE (API 31)
    }
}
