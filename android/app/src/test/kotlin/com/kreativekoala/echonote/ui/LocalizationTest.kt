package com.kreativekoala.echonote.ui

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.AccurateSupport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {
    private val keys = listOf(
        "acknowledgments_models_title", "acknowledgments_whisper_desc", "acknowledgments_sherpa_desc",
        "acknowledgments_speaker_desc", "acknowledgments_fonts_desc", "acknowledgments_view_licenses", "licenses_title"
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
    fun accurateIsOnlyOfferedWhereWhisperBaseBeatVoskInTheBenchmark() {
        val supported = TranscriptionLanguage.entries.filter { AccurateSupport.isSupported(it) }.toSet()
        assertEquals(
            setOf(
                TranscriptionLanguage.ENGLISH, TranscriptionLanguage.SPANISH, TranscriptionLanguage.GERMAN,
                TranscriptionLanguage.PORTUGUESE, TranscriptionLanguage.KOREAN
            ),
            supported
        )
        // Hindi is the case where Whisper base outputs garbage, so it must never be routed to Whisper.
        assertFalse(AccurateSupport.isSupported(TranscriptionLanguage.HINDI))
    }
}
