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
    fun smallPhonesFallBackToNemoForEnglishAndVoskForTheOtherWhisperLanguages() {
        val fourGb = 3_800_000_000L
        val sixGb = 5_700_000_000L
        // NeMo beat Vosk by 12 WER points on 120 real English clips and 7 on FLEURS (both CIs exclude 0) and peaks near 0.4 GB
        assertEquals(AsrEngine.NEMO_FAST_CONFORMER, EnginePlan.forLanguage(TranscriptionLanguage.ENGLISH, fourGb))
        assertEquals(AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(TranscriptionLanguage.ENGLISH, sixGb))
        // NeMo has no pt, ja or ko, so those still fall back to Vosk
        listOf(TranscriptionLanguage.PORTUGUESE, TranscriptionLanguage.JAPANESE, TranscriptionLanguage.KOREAN).forEach {
            assertEquals(it.name, AsrEngine.VOSK, EnginePlan.forLanguage(it, fourGb))
            assertEquals(it.name, AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(it, sixGb))
        }
        // SenseVoice peaked at about 0.5 GB, so a small phone still gets it
        assertEquals(AsrEngine.SENSE_VOICE, EnginePlan.forLanguage(TranscriptionLanguage.CHINESE, fourGb))
        assertTrue(EnginePlan.bestForLanguage(TranscriptionLanguage.ENGLISH) == AsrEngine.WHISPER_SMALL)
        // NeMo is small enough for any phone, so it is not memory-gated
        assertEquals(AsrEngine.NEMO_FAST_CONFORMER, EnginePlan.forLanguage(TranscriptionLanguage.GERMAN, fourGb))
    }

    @Test
    fun theMemoryThresholdIsExclusiveAndEveryLanguageHasAnAnswerAtEveryRamSize() {
        val limit = EnginePlan.WHISPER_SMALL_MIN_RAM_BYTES
        val whisperLanguages = listOf(
            TranscriptionLanguage.ENGLISH, TranscriptionLanguage.PORTUGUESE, TranscriptionLanguage.JAPANESE, TranscriptionLanguage.KOREAN
        )
        for (lang in whisperLanguages) {
            assertEquals(lang.name, AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(lang, limit))
            assertEquals(lang.name, AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(lang, limit + 1))
            assertEquals(lang.name, EnginePlan.lowRamFallback(lang), EnginePlan.forLanguage(lang, limit - 1))
            assertEquals(lang.name, EnginePlan.lowRamFallback(lang), EnginePlan.forLanguage(lang, 0L))
            assertEquals(lang.name, AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(lang))
        }
        // Languages whose best engine fits any phone do not change with memory
        for (lang in TranscriptionLanguage.entries.filter { EnginePlan.bestForLanguage(it).minRamBytes == 0L }) {
            val best = EnginePlan.bestForLanguage(lang)
            for (ram in listOf(0L, 3_800_000_000L, limit - 1, limit, 5_700_000_000L, 12_000_000_000L)) {
                assertEquals(lang.name, best, EnginePlan.forLanguage(lang, ram))
            }
        }
        // For the ten languages that shipped first the fallback is unchanged: Vosk, or NeMo for English
        for (lang in TranscriptionLanguage.entries.take(10)) {
            val fallback = EnginePlan.lowRamFallback(lang)
            assertTrue(lang.name, fallback == AsrEngine.VOSK || (fallback == AsrEngine.NEMO_FAST_CONFORMER && lang == TranscriptionLanguage.ENGLISH) ||
                EnginePlan.bestForLanguage(lang).minRamBytes == 0L)
        }
    }

    @Test
    fun theLowMemoryHintStringsExistInEveryLocale() {
        val dirs = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        assertTrue(dirs.size >= 20)
        for (dir in dirs) {
            val text = File(dir, "strings_lowram.xml").readText()
            assertTrue(dir.name, text.contains("name=\"settings_accurate_lowram_fast\"") && text.contains("name=\"settings_accurate_lowram_smaller\""))
            assertTrue("${dir.name} lacks the language argument", text.contains("%1\$" + "s"))
            assertTrue("${dir.name} lacks the size argument", text.contains("%1\$" + "d"))
        }
    }

    private val resDir = File("src/main/res")

    private fun stringKeys(file: File): Set<String> =
        Regex("<string name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private fun args(file: File): Map<String, Set<String>> =
        Regex("<string name=\"([^\"]+)\"[^>]*>([^<]*)<").findAll(file.readText())
            .associate { it.groupValues[1] to Regex("%\\d+\\$[sd]").findAll(it.groupValues[2]).map { m -> m.value }.toSet() }

    @Test
    fun theFirstRunAndLanguagePickerStringsExistInEveryLocaleWithTheSameFormatArguments() {
        val locales = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
        assertEquals(19, locales.size)
        for (name in listOf("strings_firstrun.xml", "strings_languages.xml")) {
            val base = File(resDir, "values/$name")
            val keys = stringKeys(base)
            val baseArgs = args(base)
            assertTrue("$name has no keys", keys.isNotEmpty())
            for (dir in locales) {
                val f = File(dir, name)
                assertTrue("${dir.name} lacks $name", f.exists())
                assertEquals("${dir.name}/$name keys", keys, stringKeys(f))
                val got = args(f)
                for (k in keys) assertEquals("${dir.name}/$name $k format arguments", baseArgs[k], got[k])
            }
        }
    }

    @Test
    fun theAppLanguageListNamesEveryTranslatedLocale() {
        val listed = Regex("android:name=\"([^\"]+)\"").findAll(File(resDir, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }.toList()
        assertEquals("no duplicates", listed.size, listed.toSet().size)
        // values-in is the legacy folder for Indonesian ("id"); values-pt-rBR and values-zh-rCN carry their region
        fun tagFor(folder: String): String = when (val q = folder.removePrefix("values-")) {
            "in" -> "id"
            "pt-rBR" -> "pt-BR"
            "zh-rCN" -> "zh-CN"
            else -> q
        }
        val folders = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .map { tagFor(it.name) }.toSet()
        assertEquals(19, folders.size)
        assertEquals("every shipped locale must be pickable per app on Android 13+", folders + "en", listed.toSet())
    }
}
