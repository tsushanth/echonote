package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import kotlin.math.roundToInt

/** What switching from Fast to Accurate would buy for a language on this phone, from the app's own benchmark table. */
data class EngineAdvice(val fastErrorPct: Int, val accurateErrorPct: Int, val accurateMb: Int)

/**
 * Fast (the default) is the small Vosk model. For some languages the Accurate engine is far better, and the picker's
 * badge describes Accurate. This finds those languages so the app can say so once, in a line, and let the user opt in:
 * Accurate is a larger download, so it is never switched on silently.
 */
object EngineAdvisor {
    /** Fast must be at least this many points worse than Accurate... */
    const val MIN_GAIN_POINTS = 7.0

    /** ...and at least this many times worse (so 29.8 vs 22.0 for Korean is not a "clearly better" case). */
    const val MIN_RATIO = 1.4

    /**
     * Advice for [language], or null when Fast is as good (Spanish, Italian, Hindi, Japanese, ...), when Fast is unusable
     * anyway (Accurate is then always used and there is nothing to choose), when the phone cannot run a better engine,
     * or when the better engine is also Vosk.
     */
    fun adviceFor(language: TranscriptionLanguage, totalMemBytes: Long = Long.MAX_VALUE): EngineAdvice? {
        if (!EnginePlan.fastIsUsable(language)) return null
        val fast = LanguageSupport.voskErrorPct(language) ?: return null
        val run = EnginePlan.resolve(language, totalMemBytes) as? Resolution.Run ?: return null
        if (run.engine == AsrEngine.VOSK) return null
        val accurate = run.pick.errorPct
        if (fast - accurate < MIN_GAIN_POINTS || fast < accurate * MIN_RATIO) return null
        return EngineAdvice(fast.roundToInt(), accurate.roundToInt(), run.engine.downloadMb)
    }
}
