package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which transcription language a phone starts with, and that a stored choice is never overridden. */
class LanguageDefaultsTest {
    private fun tag(t: String) = LanguageDefaults.fromLocale(Locale.forLanguageTag(t))

    @Test
    fun regionAndScriptAreIgnored() {
        assertEquals(TranscriptionLanguage.PORTUGUESE, tag("pt-BR"))
        assertEquals(TranscriptionLanguage.PORTUGUESE, tag("pt-PT"))
        assertEquals(TranscriptionLanguage.PORTUGUESE, tag("pt"))
        assertEquals(TranscriptionLanguage.SPANISH, tag("es-MX"))
        assertEquals(TranscriptionLanguage.SPANISH, tag("es-419"))
        assertEquals(TranscriptionLanguage.ENGLISH, tag("en-IN"))
        assertEquals(TranscriptionLanguage.ENGLISH, tag("en-NG"))
        assertEquals(TranscriptionLanguage.HINDI, tag("hi-IN"))
        assertEquals(TranscriptionLanguage.BENGALI, tag("bn-BD"))
        assertEquals(TranscriptionLanguage.ARABIC, tag("ar-EG"))
        assertEquals(TranscriptionLanguage.FRENCH, tag("fr-CA"))
    }

    @Test
    fun chineseInEveryScriptAndRegionIsChinese() {
        for (t in listOf("zh", "zh-CN", "zh-Hans", "zh-Hans-CN", "zh-TW", "zh-Hant", "zh-Hant-TW", "zh-HK", "zh-SG")) {
            assertEquals(t, TranscriptionLanguage.CHINESE, tag(t))
        }
    }

    @Test
    fun indonesianAndFilipinoLegacyCodes() {
        assertEquals(TranscriptionLanguage.INDONESIAN, LanguageDefaults.fromLanguageCode("id"))
        assertEquals(TranscriptionLanguage.INDONESIAN, LanguageDefaults.fromLanguageCode("in"))
        assertEquals(TranscriptionLanguage.INDONESIAN, LanguageDefaults.fromLocale(Locale("in", "ID")))
        assertEquals(TranscriptionLanguage.INDONESIAN, tag("id-ID"))
        assertEquals(TranscriptionLanguage.TAGALOG, LanguageDefaults.fromLanguageCode("fil"))
        assertEquals(TranscriptionLanguage.TAGALOG, LanguageDefaults.fromLanguageCode("tl"))
        assertEquals(TranscriptionLanguage.TAGALOG, tag("fil-PH"))
        assertEquals(TranscriptionLanguage.MALAY, tag("ms-MY"))
    }

    @Test
    fun hebrewUrduAndOtherUnsupportedLanguagesAreNotMapped() {
        assertNull(LanguageDefaults.fromLanguageCode("he"))
        assertNull(LanguageDefaults.fromLanguageCode("iw"))
        assertNull(LanguageDefaults.fromLocale(Locale("iw", "IL")))
        assertNull(tag("ur-PK"))
        assertNull(tag("nb-NO"))
        assertNull(tag("no"))
        assertNull(tag("yue-HK"))
        assertNull(LanguageDefaults.fromLanguageCode(null))
        assertNull(LanguageDefaults.fromLanguageCode(""))
    }

    @Test
    fun codesAreCaseInsensitive() {
        assertEquals(TranscriptionLanguage.GERMAN, LanguageDefaults.fromLanguageCode("DE"))
        assertEquals(TranscriptionLanguage.GERMAN, LanguageDefaults.fromLanguageCode(" de "))
    }

    @Test
    fun everySupportedLanguageIsReachableFromItsOwnTag() {
        for (lang in TranscriptionLanguage.entries) {
            assertEquals(lang.name, lang, LanguageDefaults.fromLocale(Locale.forLanguageTag(lang.bcp47Tag)))
            assertEquals(lang.name, lang, LanguageDefaults.fromLanguageCode(lang.engineCode))
        }
    }

    @Test
    fun theFirstSupportedLocaleInThePhonesListWins() {
        val hi = Locale.forLanguageTag("hi-IN")
        val en = Locale.forLanguageTag("en-IN")
        assertEquals(TranscriptionLanguage.HINDI, LanguageDefaults.fromLocales(listOf(hi, en)))
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.fromLocales(listOf(en, hi)))
        // An unsupported first language falls through to the next supported one
        assertEquals(TranscriptionLanguage.HINDI, LanguageDefaults.fromLocales(listOf(Locale.forLanguageTag("ur-PK"), hi, en)))
        assertEquals(TranscriptionLanguage.SPANISH, LanguageDefaults.fromLocales(listOf(Locale.forLanguageTag("he-IL"), Locale.forLanguageTag("es-US"))))
    }

    @Test
    fun anUnsupportedOrEmptyPhoneLanguageDefaultsToEnglish() {
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.fromLocales(listOf(Locale.forLanguageTag("ur-PK"))))
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.fromLocales(listOf(Locale.forLanguageTag("he-IL"), Locale.forLanguageTag("ur"))))
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.fromLocales(emptyList()))
    }

    @Test
    fun aStoredChoiceIsNeverOverriddenByThePhoneLanguage() {
        for (lang in TranscriptionLanguage.entries) {
            assertEquals(lang, LanguageDefaults.resolve(lang.name, TranscriptionLanguage.HINDI))
        }
        // An English user on a Spanish phone who picked English stays on English
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.resolve("ENGLISH", TranscriptionLanguage.SPANISH))
    }

    @Test
    fun withNoValidStoredChoiceThePhoneDefaultIsUsed() {
        assertEquals(TranscriptionLanguage.SPANISH, LanguageDefaults.resolve(null, TranscriptionLanguage.SPANISH))
        assertEquals(TranscriptionLanguage.SPANISH, LanguageDefaults.resolve("NO_SUCH_LANGUAGE", TranscriptionLanguage.SPANISH))
        assertEquals(TranscriptionLanguage.ENGLISH, LanguageDefaults.resolve(null, TranscriptionLanguage.ENGLISH))
    }
}
