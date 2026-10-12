package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** The speech recognizers the app can run. */
enum class AsrEngine(val displayName: String, val downloadMb: Int) {
    /** Small Kaldi models; the Fast option, one per language, downloaded on first use. */
    VOSK("Vosk", 0),
    WHISPER_SMALL("Whisper small", 375),
    NEMO_FAST_CONFORMER("NeMo FastConformer", 103),
    SENSE_VOICE("SenseVoice", 237)
}

/**
 * Which engine "Accurate" uses for each language, chosen from a benchmark of the app's own models on
 * 25 FLEURS read-speech clips per language (tools/asr-bench, ml_*.py). Error rate %, lower is better
 * (WER; CER for Chinese, Japanese, Korean):
 *
 *   lang  Vosk  Whisper-small  NeMo FC  SenseVoice   chosen
 *   en    24.0       8.9        16.2        -        Whisper small (clearly better, CI excludes 0)
 *   pt    29.6       6.3          -         -        Whisper small
 *   ja    22.2      20.0          -        78 (broken)  Whisper small
 *   ko    29.8      22.0          -        79 (broken)  Whisper small
 *   de    23.8       9.1         9.1        -        NeMo (tied; 3.6x smaller, ~15x faster, real word times)
 *   es    11.5       5.6         6.9        -        NeMo (tied: CI -5.1..+2.0 pts; same reasons)
 *   fr    19.4      12.8        11.5        -        NeMo (better, though within noise)
 *   zh    24.2      39.3          -        11.9      SenseVoice
 *   it     9.0       9.8          -         -        Vosk (nothing is better)
 *   hi    16.4      90.7          -         -        Vosk (Whisper is unusable)
 *
 * Where Vosk is the choice, Accurate and speaker labels simply use Fast.
 *
 * On a phone with less than [WHISPER_SMALL_MIN_RAM_BYTES] of RAM, the Whisper-small languages fall back:
 *
 *   en       NeMo FC. Same 28 s windows, peaked ~383 MB on a Pixel 9 Pro (Whisper small ~1.3 GB). WER vs Vosk, paired
 *            bootstrap, Vosk minus NeMo: +12.1 points on 120 real ESB clips (CI 10.0..14.3), +7.1 on 25 FLEURS (CI 2.3..12.3).
 *            It is still worse than Whisper small (ESB 9.9 vs 8.9, FLEURS 17.8 vs 11.0 with the Whisper English normalizer).
 *   pt ja ko Vosk. NeMo does not cover them. Whisper base would be 55% of small's memory but was only clearly better for pt
 *            (29.6 to 16.7); ja is worse than Vosk and ko is within noise, so it is not shipped as a fallback.
 */
object EnginePlan {
    /**
     * Whisper small peaked at about 1.3 GB of memory on a Pixel 9 Pro (SenseVoice about 0.5 GB). A 4 GB phone
     * reports about 3.8e9 bytes of RAM and a 6 GB phone about 5.7e9, so below 5e9 it is not used.
     */
    const val WHISPER_SMALL_MIN_RAM_BYTES = 5_000_000_000L

    /** The engine best for [language], ignoring how much memory the phone has. */
    fun bestForLanguage(language: TranscriptionLanguage): AsrEngine = when (language) {
        TranscriptionLanguage.CHINESE -> AsrEngine.SENSE_VOICE
        TranscriptionLanguage.GERMAN, TranscriptionLanguage.SPANISH, TranscriptionLanguage.FRENCH -> AsrEngine.NEMO_FAST_CONFORMER
        TranscriptionLanguage.ITALIAN, TranscriptionLanguage.HINDI -> AsrEngine.VOSK
        else -> AsrEngine.WHISPER_SMALL
    }

    /** What a phone below [WHISPER_SMALL_MIN_RAM_BYTES] runs in place of Whisper small: NeMo for English, Vosk otherwise. */
    fun lowRamFallback(language: TranscriptionLanguage): AsrEngine =
        if (language == TranscriptionLanguage.ENGLISH) AsrEngine.NEMO_FAST_CONFORMER else AsrEngine.VOSK

    /** The engine to run: the best one, or [lowRamFallback] when the phone is too small for Whisper small. */
    fun forLanguage(language: TranscriptionLanguage, totalMemBytes: Long = Long.MAX_VALUE): AsrEngine {
        val best = bestForLanguage(language)
        return if (best == AsrEngine.WHISPER_SMALL && totalMemBytes < WHISPER_SMALL_MIN_RAM_BYTES) lowRamFallback(language) else best
    }

    /** Whisper needs a language code; SenseVoice takes the same ISO codes. */
    fun languageCode(language: TranscriptionLanguage): String = language.bcp47Tag.substringBefore('-')
}
