package com.kreativekoala.echonote.ui

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.AsrEngine
import com.kreativekoala.echonote.service.EnginePlan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {
    private val keys = listOf(
        "acknowledgments_models_title", "acknowledgments_whisper_desc", "acknowledgments_sherpa_desc",
        "acknowledgments_speaker_desc", "acknowledgments_nemo_desc", "acknowledgments_fonts_desc", "acknowledgments_view_licenses", "licenses_title"
    )

    @Test
    fun everyTranslatedLocaleHasEveryCreditString() {
        val dirs = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        assertTrue("expected the default plus translated locales", dirs.size >= 20)
        for (dir in dirs) {
            val text = File(dir, "strings.xml").takeIf { it.exists() }?.readText() ?: continue
            for (key in keys) assertTrue("${dir.name} is missing $key", text.contains("name=\"$key\""))
        }
    }

    @Test
    fun theVersionStringIsAFormatSoItShowsTheRealVersion() {
        val dirs = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        val format = Regex("name=\"settings_version_format\">[^<]*%1\\\$" + "s[^<]*<")
        for (dir in dirs) {
            val text = File(dir, "strings.xml").takeIf { it.exists() }?.readText() ?: continue
            assertTrue("${dir.name} lacks settings_version_format", format.containsMatchIn(text))
            assertFalse("${dir.name} still has the hard-coded version", text.contains("name=\"settings_version\""))
        }
    }

    @Test
    fun eachLanguageUsesTheEngineThatWonItsBenchmark() {
        val plan = TranscriptionLanguage.entries.associateWith { EnginePlan.forLanguage(it) }
        assertEquals(AsrEngine.SENSE_VOICE, plan[TranscriptionLanguage.CHINESE])
        // Whisper base garbles Hindi and Vosk is better for Italian, so neither may be routed to Whisper.
        assertEquals(AsrEngine.VOSK, plan[TranscriptionLanguage.HINDI])
        assertEquals(AsrEngine.VOSK, plan[TranscriptionLanguage.ITALIAN])
        listOf(
            TranscriptionLanguage.ENGLISH, TranscriptionLanguage.PORTUGUESE, TranscriptionLanguage.JAPANESE, TranscriptionLanguage.KOREAN
        ).forEach { assertEquals(it.name, AsrEngine.WHISPER_SMALL, plan[it]) }
        // NeMo tied Whisper small on these three (bootstrap CIs span 0) and is smaller, faster, with real word times
        listOf(TranscriptionLanguage.GERMAN, TranscriptionLanguage.SPANISH, TranscriptionLanguage.FRENCH)
            .forEach { assertEquals(it.name, AsrEngine.NEMO_FAST_CONFORMER, plan[it]) }
    }

    @Test
    fun smallPhonesFallBackToVoskWhereWhisperSmallWouldBeUsed() {
        val fourGb = 3_800_000_000L
        val sixGb = 5_700_000_000L
        assertEquals(AsrEngine.VOSK, EnginePlan.forLanguage(TranscriptionLanguage.ENGLISH, fourGb))
        assertEquals(AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(TranscriptionLanguage.ENGLISH, sixGb))
        // SenseVoice peaked at about 0.5 GB, so a small phone still gets it
        assertEquals(AsrEngine.SENSE_VOICE, EnginePlan.forLanguage(TranscriptionLanguage.CHINESE, fourGb))
        assertTrue(EnginePlan.bestForLanguage(TranscriptionLanguage.ENGLISH) == AsrEngine.WHISPER_SMALL)
        // NeMo is small enough for any phone, so it is not memory-gated
        assertEquals(AsrEngine.NEMO_FAST_CONFORMER, EnginePlan.forLanguage(TranscriptionLanguage.GERMAN, fourGb))
    }
}
