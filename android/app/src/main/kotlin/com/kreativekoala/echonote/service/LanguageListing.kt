package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** How one language appears in the picker on a phone with a given amount of memory. */
sealed interface LanguageRow {
    val sortName: String

    /** Every name this row can be found by in the picker's search: English name, native name. */
    val searchNames: List<String>

    /** A language the phone can transcribe. [pick] is what will run in Accurate; [downgraded] is true when the best engine did not fit. */
    data class Available(
        val language: TranscriptionLanguage,
        val pick: EnginePick,
        val best: EnginePick,
        val fastMb: Int?,
        val fastUsable: Boolean
    ) : LanguageRow {
        override val sortName: String get() = language.displayName
        override val searchNames: List<String> get() = listOf(language.displayName, language.nativeName, language.bcp47Tag)
        val level: SupportLevel get() = LanguageSupport.levelFor(language, pick)
        val downgraded: Boolean get() = pick.engine != best.engine
        /** The engine is an optional large download (over 400 MB). */
        val largeDownload: Boolean get() = pick.engine.downloadMb > EnginePlan.DOWNLOAD_BUDGET_MB
    }

    /** Offered, but every engine needs more memory than this phone has and there is no Fast model. */
    data class NeedsBiggerPhone(val language: TranscriptionLanguage, val best: EnginePick) : LanguageRow {
        override val sortName: String get() = language.displayName
        override val searchNames: List<String> get() = listOf(language.displayName, language.nativeName, language.bcp47Tag)
    }

    /** Measured, did not reach the bar. [closest] is the supported language it is nearest to, if any. */
    data class NotYet(val entry: NotOffered) : LanguageRow {
        override val sortName: String get() = entry.displayName
        override val searchNames: List<String> get() = listOf(entry.displayName, entry.nativeName)
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

    /**
     * The rows whose English or native name (or language tag) contains [query], ignoring case and surrounding spaces;
     * all rows for a blank query. Order and grouping are kept.
     */
    fun filter(rows: List<LanguageRow>, query: String): List<LanguageRow> {
        val q = query.trim()
        if (q.isEmpty()) return rows
        return rows.filter { row -> row.searchNames.any { it.contains(q, ignoreCase = true) } }
    }
}
