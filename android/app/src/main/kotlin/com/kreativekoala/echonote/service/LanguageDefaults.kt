package com.kreativekoala.echonote.service

import android.content.Context
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import java.util.Locale

/**
 * Which transcription language a phone starts with. Until the user chooses one, the transcription language follows
 * the language the phone (or the per-app language setting) is set to, when that is one of the supported languages,
 * and is English otherwise. A language the user has chosen is never overridden (see [resolve]).
 */
object LanguageDefaults {
    /**
     * ISO 639 language codes (and the legacy or alternative forms Android and Java still emit) mapped to a language.
     * "in" is the old code for Indonesian, "fil" is Filipino (the app transcribes it as Tagalog, "tl"). Hebrew
     * ("he", legacy "iw") and Urdu are deliberately absent: they are not offered, so a phone set to them falls
     * through to the next language in the list, or to English.
     */
    private val BY_CODE: Map<String, TranscriptionLanguage> = buildMap {
        for (lang in TranscriptionLanguage.entries) {
            put(lang.bcp47Tag.substringBefore('-').lowercase(Locale.ROOT), lang)
            put(lang.engineCode.lowercase(Locale.ROOT), lang)
        }
        put("in", TranscriptionLanguage.INDONESIAN)
        put("id", TranscriptionLanguage.INDONESIAN)
        put("fil", TranscriptionLanguage.TAGALOG)
        put("tl", TranscriptionLanguage.TAGALOG)
    }

    /** The language for a bare language code such as "pt", "in" or "fil" (case-insensitive), or null if it is not supported. */
    fun fromLanguageCode(code: String?): TranscriptionLanguage? {
        if (code.isNullOrBlank()) return null
        return BY_CODE[code.trim().lowercase(Locale.ROOT)]
    }

    /**
     * The language for [locale], ignoring region and script (pt-BR and pt-PT are both Portuguese, zh-Hans-CN and
     * zh-Hant-TW are both Chinese: the recognizer is Mandarin and writes simplified characters), or null.
     */
    fun fromLocale(locale: Locale): TranscriptionLanguage? = fromLanguageCode(locale.language)

    /** The first locale in [locales] (the phone's preference order) that is a supported language, else English. */
    fun fromLocales(locales: List<Locale>): TranscriptionLanguage =
        locales.firstNotNullOfOrNull { fromLocale(it) } ?: TranscriptionLanguage.ENGLISH

    /** The default for the app's current language list: the per-app language when set, else the phone's languages. */
    fun forDevice(context: Context): TranscriptionLanguage {
        val list = context.resources.configuration.locales
        return fromLocales((0 until list.size()).map { list[it] })
    }

    /**
     * The language to use: the stored choice when there is a valid one, else [deviceDefault]. A stored value is the
     * enum name; one that no longer parses is treated as not chosen.
     */
    fun resolve(stored: String?, deviceDefault: TranscriptionLanguage): TranscriptionLanguage =
        stored?.let { name -> TranscriptionLanguage.entries.firstOrNull { it.name == name } } ?: deviceDefault
}
