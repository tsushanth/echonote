package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** When the app tells the user that Accurate is far better than the default Fast, from its own benchmark table. */
class EngineAdvisorTest {
    private val fourGb = 3_800_000_000L
    private val sixGb = 5_700_000_000L

    @Test
    fun portugueseGetsTheBigGainTheBenchmarkShows() {
        val a = EngineAdvisor.adviceFor(TranscriptionLanguage.PORTUGUESE, sixGb)!!
        assertEquals(30, a.fastErrorPct)      // Vosk 29.6
        assertEquals(6, a.accurateErrorPct)   // Whisper small 6.3
        assertEquals(375, a.accurateMb)
    }

    @Test
    fun russianGermanFrenchTurkishAndChineseGetAdvice() {
        for (lang in listOf(
            TranscriptionLanguage.RUSSIAN, TranscriptionLanguage.GERMAN, TranscriptionLanguage.FRENCH,
            TranscriptionLanguage.TURKISH, TranscriptionLanguage.CHINESE, TranscriptionLanguage.ENGLISH
        )) assertNotNull(lang.name, EngineAdvisor.adviceFor(lang, sixGb))
    }

    @Test
    fun noAdviceWhereFastIsAsGoodOrTheGainIsSmall() {
        // Spanish 11.5 vs 6.9, Japanese 22.2 vs 20.0, Korean 29.8 vs 22.0 (a 1.35x gap): not "clearly better"
        for (lang in listOf(TranscriptionLanguage.SPANISH, TranscriptionLanguage.JAPANESE, TranscriptionLanguage.KOREAN)) {
            assertNull(lang.name, EngineAdvisor.adviceFor(lang, sixGb))
        }
        // Italian and Hindi: Vosk is the best engine, so Accurate is the same model
        assertNull(EngineAdvisor.adviceFor(TranscriptionLanguage.ITALIAN, sixGb))
        assertNull(EngineAdvisor.adviceFor(TranscriptionLanguage.HINDI, sixGb))
    }

    @Test
    fun dutchFastIsBarelyUsableAndAccurateIsFarBetter() {
        // Vosk Dutch 48.7 vs Whisper small 21.7
        val a = EngineAdvisor.adviceFor(TranscriptionLanguage.DUTCH, sixGb)!!
        assertEquals(49, a.fastErrorPct)
        assertEquals(22, a.accurateErrorPct)
    }

    @Test
    fun noAdviceWhereFastIsUnusableBecauseAccurateIsAlwaysUsedAnyway() {
        for (lang in listOf(TranscriptionLanguage.BENGALI, TranscriptionLanguage.ARABIC, TranscriptionLanguage.THAI, TranscriptionLanguage.CZECH)) {
            assertNull(lang.name, EngineAdvisor.adviceFor(lang, sixGb))
        }
    }

    @Test
    fun theAdviceFollowsTheEngineThatActuallyRunsOnASmallPhone() {
        // 4 GB phone: English runs NeMo (16.2, 103 MB), not Whisper (8.9, 375 MB), so that is the engine and size quoted
        val en = EngineAdvisor.adviceFor(TranscriptionLanguage.ENGLISH, fourGb)!!
        assertEquals(16, en.accurateErrorPct)
        assertEquals(103, en.accurateMb)
        assertEquals(9, EngineAdvisor.adviceFor(TranscriptionLanguage.ENGLISH, sixGb)!!.accurateErrorPct)
        // 4 GB phone: Portuguese would fall back to Vosk itself, so there is nothing better to offer
        assertNull(EngineAdvisor.adviceFor(TranscriptionLanguage.PORTUGUESE, fourGb))
        // German's engine (NeMo) fits any phone
        assertNotNull(EngineAdvisor.adviceFor(TranscriptionLanguage.GERMAN, fourGb))
    }

    @Test
    fun everyAdviceIsRealGain() {
        for (lang in TranscriptionLanguage.entries) {
            val a = EngineAdvisor.adviceFor(lang) ?: continue
            assertEquals(lang.name, true, a.fastErrorPct - a.accurateErrorPct >= 6) // rounding can cost a point
            assertEquals(lang.name, true, a.accurateMb > 0)
        }
    }
}
