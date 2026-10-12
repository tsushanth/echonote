package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/**
 * Where the Accurate engine (Whisper base, int8) is actually better than Fast (Vosk small).
 *
 * Measured on 25 FLEURS read-speech utterances per language (tools/asr-bench, ml_*.py), error rate %
 * (WER, or CER for Chinese, Japanese and Korean), Whisper base vs Vosk small:
 *   en 10.9 vs 24.0   pt 16.7 vs 29.6   de 20.9 vs 23.8   es 10.7 vs 11.5   ko 25.0 vs 29.8   -> Whisper
 *   fr 32.2 vs 19.4   it 21.2 vs  9.0   ja 27.9 vs 22.2   zh 46.9 vs 24.2   hi 115 vs 16.4     -> Vosk
 * Hindi is the worst case: Whisper base emits a repeating loop of garbage. Where Whisper is not better,
 * Accurate (and speaker labels, which ride on it) quietly uses Fast instead of making the text worse.
 */
object AccurateSupport {
    private val whisperBeatsVosk = setOf(
        TranscriptionLanguage.ENGLISH,
        TranscriptionLanguage.SPANISH,
        TranscriptionLanguage.GERMAN,
        TranscriptionLanguage.PORTUGUESE,
        TranscriptionLanguage.KOREAN
    )

    fun isSupported(language: TranscriptionLanguage): Boolean = language in whisperBeatsVosk
}
