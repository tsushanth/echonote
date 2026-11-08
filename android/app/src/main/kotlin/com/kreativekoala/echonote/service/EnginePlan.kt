package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/**
 * The speech recognizers the app can run.
 *
 * [downloadMb] is what the user downloads once (the archive or the files). [minRamBytes] is the phone memory below
 * which the engine is not used. [wordTimes] says whether the engine returns token times; without them (Whisper) the
 * words are spread over the speech by [distributeOverSpeech]. [upperCaseOutput] marks engines that write capitals.
 * Peak memory and speed were measured on a Mac with the same files (docs/LANGUAGES.md); on-phone memory was measured
 * for the first four only.
 */
enum class AsrEngine(
    val displayName: String,
    val downloadMb: Int,
    val minRamBytes: Long = 0L,
    val wordTimes: Boolean = true,
    val upperCaseOutput: Boolean = false
) {
    /** Small Kaldi models; the Fast option, one per language, downloaded on first use. */
    VOSK("Vosk", 0),
    WHISPER_SMALL("Whisper small", 375, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES, wordTimes = false),
    NEMO_FAST_CONFORMER("NeMo FastConformer", 103),
    SENSE_VOICE("SenseVoice", 237),

    /** NVIDIA FastConformer CTC for Belarusian, German, English, Spanish, French, Croatian, Italian, Polish, Russian, Ukrainian. */
    NEMO_MULTILINGUAL("NeMo FastConformer 10-language", 102),

    /** PengCheng Starling streaming Zipformer for Arabic, English, Indonesian, Japanese, Russian, Thai, Vietnamese, Chinese. */
    STARLING("Starling multilingual", 259, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES),
    ZIPFORMER_RU("Zipformer Russian", 60, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES),
    ZIPFORMER_VI("Zipformer Vietnamese", 60, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES, upperCaseOutput = true),

    /** Streaming Zipformer for Bengali (from the Vosk project). */
    STREAMING_BN("Zipformer Bengali", 87),

    /** DataoceanAI Dolphin base CTC, 40 Eastern languages; the small-memory engine for Thai and the Arabic/Indonesian fallback. */
    DOLPHIN_BASE("Dolphin base", 81),

    /** Meta Omnilingual ASR 300M CTC, 1,600 languages. */
    OMNILINGUAL_300M("Omnilingual 300M", 293, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES);
}

/** The tiers the picker shows. Anything above [EnginePlan.EXPERIMENTAL_MAX_ERROR] is not offered. */
enum class SupportLevel(val label: String) {
    SUPPORTED("Supported"),
    EXPERIMENTAL("Experimental");

    companion object {
        /** The tier for a measured error rate (word error rate; character error rate for Chinese, Japanese, Korean, Thai), or null if too high. */
        fun forError(errorPct: Double): SupportLevel? = when {
            errorPct <= EnginePlan.SUPPORTED_MAX_ERROR -> SUPPORTED
            errorPct <= EnginePlan.EXPERIMENTAL_MAX_ERROR -> EXPERIMENTAL
            else -> null
        }
    }
}

/** An engine for a language and the error rate (%) it scored on 25 FLEURS read-speech clips; see [LanguageSupport]. */
data class EnginePick(val engine: AsrEngine, val errorPct: Double) {
    val level: SupportLevel get() = SupportLevel.forError(errorPct) ?: error("$engine at $errorPct% is not offered")
}

/** What the app will do for a language on this phone. */
sealed interface Resolution {
    /** Run [pick]. */
    data class Run(val language: TranscriptionLanguage, val pick: EnginePick) : Resolution {
        val engine: AsrEngine get() = pick.engine
        val level: SupportLevel get() = LanguageSupport.levelFor(language, pick)
    }

    /** Every engine for this language needs more memory than the phone has. [best] is the engine that would run on a bigger phone. */
    data class NeedsBiggerPhone(val best: EnginePick) : Resolution
}

/**
 * Which engine runs each language. The per-language choices and the measurements behind them are in [LanguageSupport]
 * (generated from tools/asr-bench, see docs/LANGUAGES.md). The rule that produced them:
 *
 *  - an engine is offered for a language only if it scored at most 50% error (CER for Chinese, Japanese, Korean and
 *    Thai) on FLEURS; 30% or less is "Supported", above that "Experimental";
 *  - the best scoring engine wins, except that a smaller or already shipped engine within 2 points is preferred
 *    (25 clips cannot tell 2 points apart);
 *  - where Vosk is the chosen engine, Accurate and speaker labels simply use Fast;
 *  - on a phone with less than an engine's [AsrEngine.minRamBytes] the next engine down the list runs, and if none fits
 *    the language is reported as needing a bigger phone instead of running something that would be killed.
 */
object EnginePlan {
    /** Error rate at or below which a language is "Supported". */
    const val SUPPORTED_MAX_ERROR = 30.0

    /** Error rate at or below which a language is offered at all, as "Experimental". */
    const val EXPERIMENTAL_MAX_ERROR = 50.0

    /**
     * Whisper small peaked at about 1.3 GB of memory on a Pixel 9 Pro (SenseVoice about 0.5 GB). A 4 GB phone
     * reports about 3.8e9 bytes of RAM and a 6 GB phone about 5.7e9, so below 5e9 it is not used.
     */
    const val WHISPER_SMALL_MIN_RAM_BYTES = 5_000_000_000L

    /** Default ceiling for a model download; larger engines are offered as optional and labelled as such. */
    const val DOWNLOAD_BUDGET_MB = 400

    /**
     * Engines whose peak memory on the Mac test (28 s windows, 4 threads) was above about 0.7 GB use the same 5e9 line as
     * Whisper small: Whisper small 1.35 GB, Omnilingual 1.22 GB, Starling 0.81 GB, Zipformer Vietnamese 0.78 GB, Zipformer
     * Russian 0.74 GB. The engines below it (NeMo 0.53 GB, Dolphin base 0.54 GB, Bengali Zipformer 0.39 GB) run anywhere.
     * No phone measurement exists for any of the new engines.
     */
    const val BIG_ENGINE_MIN_RAM_BYTES = WHISPER_SMALL_MIN_RAM_BYTES

    /** The engines for [language], best first. Never empty. */
    fun picks(language: TranscriptionLanguage): List<EnginePick> = LanguageSupport.picks(language)

    /** The engine best for [language], ignoring how much memory the phone has. */
    fun bestForLanguage(language: TranscriptionLanguage): AsrEngine = picks(language).first().engine

    /** What to run, or [Resolution.NeedsBiggerPhone] when no engine for [language] fits in [totalMemBytes]. */
    fun resolve(language: TranscriptionLanguage, totalMemBytes: Long = Long.MAX_VALUE): Resolution {
        val all = picks(language)
        return all.firstOrNull { it.engine.minRamBytes <= totalMemBytes }?.let { Resolution.Run(language, it) }
            ?: Resolution.NeedsBiggerPhone(all.first())
    }

    /** What a phone below [WHISPER_SMALL_MIN_RAM_BYTES] runs in place of the best engine, or null if the best engine already fits. */
    fun lowRamFallback(language: TranscriptionLanguage): AsrEngine? =
        picks(language).firstOrNull { it.engine.minRamBytes == 0L }?.engine

    /**
     * The engine to run: the best one, or the next that fits the phone. Where nothing fits (languages with no Fast model
     * and only large engines) this returns Vosk; callers that can say "needs a bigger phone" use [resolve] instead.
     */
    fun forLanguage(language: TranscriptionLanguage, totalMemBytes: Long = Long.MAX_VALUE): AsrEngine =
        (resolve(language, totalMemBytes) as? Resolution.Run)?.engine ?: AsrEngine.VOSK

    /**
     * True when "Fast" (Vosk) is good enough to offer for [language]: it has a Vosk model and that model scored at most
     * 50%. Otherwise Fast would silently give a poor result, so the language always runs its Accurate engine.
     */
    fun fastIsUsable(language: TranscriptionLanguage): Boolean =
        language.hasVosk && (LanguageSupport.voskErrorPct(language)?.let { it <= EXPERIMENTAL_MAX_ERROR } ?: false)

    /** Whisper needs a language code; SenseVoice takes the same ISO codes. */
    fun languageCode(language: TranscriptionLanguage): String = language.engineCode
}
