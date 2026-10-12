package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** The speech recognizers the app can run. */
enum class AsrEngine(val displayName: String, val downloadMb: Int) {
    /** Small Kaldi models; the Fast option, one per language, downloaded on first use. */
    VOSK("Vosk", 0),
    WHISPER_SMALL("Whisper small", 375),
    SENSE_VOICE("SenseVoice", 237)
}

/**
 * Which engine "Accurate" uses for each language, chosen from a benchmark of the app's own models on
 * 25 FLEURS read-speech clips per language (tools/asr-bench, ml_*.py). Error rate %, lower is better
 * (WER; CER for Chinese, Japanese, Korean):
 *
 *   lang  Vosk  Whisper-base  Whisper-small  SenseVoice   chosen
 *   en    24.0     10.9           8.9            -        Whisper small
 *   es    11.5     10.7           5.6            -        Whisper small
 *   pt    29.6     16.7           6.3            -        Whisper small
 *   de    23.8     20.9           9.1            -        Whisper small
 *   fr    19.4     32.2          12.8            -        Whisper small
 *   ja    22.2     27.9          20.0           78 (broken)  Whisper small
 *   ko    29.8     25.0          22.0           79 (broken)  Whisper small
 *   zh    24.2     46.9          39.3           11.9      SenseVoice
 *   it     9.0     21.2           9.8            -        Vosk (Whisper is no better)
 *   hi    16.4    115 (garbage)  90.7            -        Vosk (no usable small Whisper)
 *
 * Where Vosk is the choice, Accurate and speaker labels simply use Fast.
 */
object EnginePlan {
    fun forLanguage(language: TranscriptionLanguage): AsrEngine = when (language) {
        TranscriptionLanguage.CHINESE -> AsrEngine.SENSE_VOICE
        TranscriptionLanguage.ITALIAN, TranscriptionLanguage.HINDI -> AsrEngine.VOSK
        else -> AsrEngine.WHISPER_SMALL
    }

    /** True when Accurate does something different from Fast for this language. */
    fun accurateDiffersFromFast(language: TranscriptionLanguage): Boolean = forLanguage(language) != AsrEngine.VOSK

    /** Whisper needs a language code; SenseVoice takes the same ISO codes. */
    fun languageCode(language: TranscriptionLanguage): String = language.bcp47Tag.substringBefore('-')
}
