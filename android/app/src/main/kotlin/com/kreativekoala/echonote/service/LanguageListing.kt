package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** How one language appears in the picker on a phone with a given amount of memory. */
sealed interface LanguageRow {
    val sortName: String

    /** A language the phone can transcribe. [pick] is what will run in Accurate; [downgraded] is true when the best engine did not fit. */
    data class Available(
        val language: TranscriptionLanguage,
        val pick: EnginePick,
        val best: EnginePick,
        val fastMb: Int?,
        val fastUsable: Boolean
    ) : LanguageRow {
        override val sortName: String get() = language.displayName
        val level: SupportLevel get() = LanguageSupport.levelFor(language, pick)
        val downgraded: Boolean get() = pick.engine != best.engine
        /** The engine is an optional large download (over 400 MB). */
        val largeDownload: Boolean get() = pick.engine.downloadMb > EnginePlan.DOWNLOAD_BUDGET_MB
    }

    /** Offered, but every engine needs more memory than this phone has and there is no Fast model. */
    data class NeedsBiggerPhone(val language: TranscriptionLanguage, val best: EnginePick) : LanguageRow {
        override val sortName: String get() = language.displayName
    }

    /** Measured, did not reach the bar. [closest] is the supported language it is nearest to, if any. */
    data class NotYet(val entry: NotOffered) : LanguageRow {
        override val sortName: String get() = entry.displayName
    }
}

object LanguageListing {
    /** Every language, grouped for display: supported first, then experimental, then bigger-phone, then not yet. */
    fun build(totalMemBytes: Long): List<LanguageRow> {
        val rows = TranscriptionLanguage.entries.map { lang ->
            when (val r = EnginePlan.resolve(lang, totalMemBytes)) {
                is Resolution.Run -> LanguageRow.Available(
                    lang, r.pick, EnginePlan.picks(lang).first(), lang.vosk?.sizeMb?.takeIf { it > 0 }, EnginePlan.fastIsUsable(lang)
                )
                is Resolution.NeedsBiggerPhone -> LanguageRow.NeedsBiggerPhone(lang, r.best)
            }
        } + LanguageSupport.NOT_OFFERED.map { LanguageRow.NotYet(it) }
        fun rank(row: LanguageRow) = when (row) {
            is LanguageRow.Available -> if (row.level == SupportLevel.SUPPORTED) 0 else 1
            is LanguageRow.NeedsBiggerPhone -> 2
            is LanguageRow.NotYet -> 3
        }
        return rows.sortedWith(compareBy<LanguageRow> { rank(it) }.thenBy { it.sortName })
    }
}
