package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The language table, the routing rules, the memory gating and the picker listing. */
class LanguageSupportTest {
    private val fourGb = 3_800_000_000L
    private val sixGb = 5_700_000_000L
    private val firstTen = TranscriptionLanguage.entries.take(10)

    // ---- list integrity -------------------------------------------------------------------------------------------

    @Test
    fun everyLanguageHasADistinctNameCodeAndModelFolder() {
        val all = TranscriptionLanguage.entries
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals("language tags must be unique", all.size, all.map { it.bcp47Tag }.toSet().size)
        assertEquals("model folders must be unique", all.size, all.map { it.modelDirName }.toSet().size)
        for (lang in all) {
            assertTrue(lang.name, lang.displayName.isNotBlank() && lang.nativeName.isNotBlank())
            assertTrue(lang.name, Regex("[a-z]{2,3}(-[A-Z]{2})?").matches(lang.bcp47Tag))
        }
    }

    @Test
    fun theFirstTenLanguagesAreUntouched() {
        // names are stored in settings and recordings; their Vosk files and folders must not change
        assertEquals(
            listOf("ENGLISH", "SPANISH", "FRENCH", "GERMAN", "PORTUGUESE", "CHINESE", "ITALIAN", "HINDI", "JAPANESE", "KOREAN"),
            firstTen.map { it.name }
        )
        assertEquals("vosk-model-small-cn-0.22.zip", TranscriptionLanguage.CHINESE.modelFileName)
        assertEquals("vosk-model-cn", TranscriptionLanguage.CHINESE.modelDirName)
        assertEquals("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", TranscriptionLanguage.ENGLISH.vosk!!.url)
        firstTen.forEach { assertNull(it.name, it.vosk!!.sha256) } // legacy downloads stay as they were
    }

    @Test
    fun everyLanguageHasPicksSortedBestFirstWithinTheBar() {
        for (lang in TranscriptionLanguage.entries) {
            val picks = EnginePlan.picks(lang)
            assertTrue(lang.name, picks.isNotEmpty())
            picks.forEach {
                assertTrue("${lang.name} ${it.engine} ${it.errorPct}", it.errorPct in 0.0..EnginePlan.EXPERIMENTAL_MAX_ERROR)
                assertNotNull(it.level)
            }
            assertEquals("${lang.name}: one pick per engine", picks.size, picks.map { it.engine }.toSet().size)
            // the first pick is never worse than a later one by more than the 2 point tie rule allows (smaller engines may win ties)
            picks.drop(1).forEach { later ->
                assertTrue("${lang.name}: ${later.engine} beats ${picks.first().engine} by more than the tie rule",
                    later.errorPct > picks.first().errorPct - 2.0 || later.engine.minRamBytes > picks.first().engine.minRamBytes)
            }
        }
    }

    @Test
    fun pickedVoskNeedsAVoskModelAndFastIsOnlyOfferedWhenVoskIsGoodEnough() {
        for (lang in TranscriptionLanguage.entries) {
            if (EnginePlan.picks(lang).any { it.engine == AsrEngine.VOSK }) assertTrue("${lang.name} picks Vosk but has no model", lang.hasVosk)
            val verr = LanguageSupport.voskErrorPct(lang)
            assertEquals("${lang.name}: a Vosk error is recorded exactly when a Vosk model is bundled", lang.hasVosk, verr != null)
            assertEquals(lang.name, lang.hasVosk && verr!! <= EnginePlan.EXPERIMENTAL_MAX_ERROR, EnginePlan.fastIsUsable(lang))
        }
    }

    @Test
    fun everyEngineThatCanBePickedHasPinnedFilesAndEveryNewVoskModelIsPinned() {
        val used = TranscriptionLanguage.entries.flatMap { EnginePlan.picks(it) }.map { it.engine }.toSet() - AsrEngine.VOSK
        for (engine in used) {
            val layout = SherpaModels.forEngine(engine)
            assertNotNull(engine.name, layout)
            for (f in layout!!.files) {
                assertTrue("${engine.name} ${f.fileName} size", f.sizeBytes > 0)
                assertTrue("${engine.name} ${f.fileName} sha", Regex("[0-9a-f]{64}").matches(f.sha256))
            }
            layout.archive?.let {
                assertTrue(engine.name, it.url.startsWith("https://"))
                assertTrue(engine.name, Regex("[0-9a-f]{64}").matches(it.sha256))
                // the advertised download size is the archive, to the nearest MB
                assertEquals(engine.name, engine.downloadMb.toDouble(), it.sizeBytes / 1_000_000.0, 1.0)
            }
        }
        for (lang in TranscriptionLanguage.entries - firstTen.toSet()) {
            val pack = lang.vosk ?: continue
            assertTrue("${lang.name} Vosk zip must be pinned", pack.sha256 != null && Regex("[0-9a-f]{64}").matches(pack.sha256!!) && pack.sizeBytes > 0)
        }
    }

    @Test
    fun modelFolderNamesNeverCollide() {
        val dirs = AsrEngine.entries.mapNotNull { SherpaModels.forEngine(it)?.dirName } + SherpaModels.DIARIZATION_DIR
        assertEquals(dirs.size, dirs.toSet().size)
        assertTrue(TranscriptionLanguage.entries.none { it.modelDirName in dirs })
    }

    // ---- routing --------------------------------------------------------------------------------------------------

    @Test
    fun arabicRunsStarlingOnABigPhoneAndDolphinOnASmallOne() {
        assertEquals(AsrEngine.STARLING, EnginePlan.forLanguage(TranscriptionLanguage.ARABIC, sixGb))
        assertEquals(AsrEngine.DOLPHIN_BASE, EnginePlan.forLanguage(TranscriptionLanguage.ARABIC, fourGb))
        // Arabic is held at Experimental because dialect speech failed a second test, whatever the read-speech score
        val run = EnginePlan.resolve(TranscriptionLanguage.ARABIC, sixGb) as Resolution.Run
        assertEquals(SupportLevel.EXPERIMENTAL, run.level)
        assertTrue(LanguageSupport.hasDialectWarning(TranscriptionLanguage.ARABIC))
        assertFalse(TranscriptionLanguage.ARABIC.hasVosk) // the only Vosk Arabic model has no stated licence
    }

    @Test
    fun smallEnginesServeSmallPhonesDirectly() {
        assertEquals(AsrEngine.NEMO_MULTILINGUAL, EnginePlan.forLanguage(TranscriptionLanguage.POLISH, fourGb))
        assertEquals(AsrEngine.NEMO_MULTILINGUAL, EnginePlan.forLanguage(TranscriptionLanguage.UKRAINIAN, fourGb))
        assertEquals(AsrEngine.DOLPHIN_BASE, EnginePlan.forLanguage(TranscriptionLanguage.THAI, fourGb))
        assertEquals(AsrEngine.STREAMING_BN, EnginePlan.forLanguage(TranscriptionLanguage.BENGALI, fourGb))
    }

    @Test
    fun russianAndVietnameseUseTheirZipformerOnBigPhonesAndASmallerEngineOnSmallOnes() {
        // Both Zipformers peaked at 0.74-0.78 GB on the Mac test, above the 0.7 GB line, so small phones get a smaller engine
        assertEquals(AsrEngine.ZIPFORMER_RU, EnginePlan.forLanguage(TranscriptionLanguage.RUSSIAN, sixGb))
        assertEquals(AsrEngine.NEMO_MULTILINGUAL, EnginePlan.forLanguage(TranscriptionLanguage.RUSSIAN, fourGb))
        assertEquals(AsrEngine.ZIPFORMER_VI, EnginePlan.forLanguage(TranscriptionLanguage.VIETNAMESE, sixGb))
        assertEquals(AsrEngine.DOLPHIN_BASE, EnginePlan.forLanguage(TranscriptionLanguage.VIETNAMESE, fourGb))
    }

    @Test
    fun turkishFallsBackToVoskOnASmallPhone() {
        assertEquals(AsrEngine.WHISPER_SMALL, EnginePlan.forLanguage(TranscriptionLanguage.TURKISH, sixGb))
        assertEquals(AsrEngine.VOSK, EnginePlan.forLanguage(TranscriptionLanguage.TURKISH, fourGb))
    }

    @Test
    fun aLanguageWithOnlyBigEnginesAndNoFastModelSaysItNeedsABiggerPhone() {
        val lang = TranscriptionLanguage.SWAHILI
        assertFalse(lang.hasVosk)
        assertEquals(AsrEngine.OMNILINGUAL_300M, EnginePlan.bestForLanguage(lang))
        val small = EnginePlan.resolve(lang, fourGb)
        assertTrue(small is Resolution.NeedsBiggerPhone)
        assertEquals(AsrEngine.OMNILINGUAL_300M, (small as Resolution.NeedsBiggerPhone).best.engine)
        assertTrue(EnginePlan.resolve(lang, sixGb) is Resolution.Run)
        // the memory line is exclusive, like Whisper small's
        assertTrue(EnginePlan.resolve(lang, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES - 1) is Resolution.NeedsBiggerPhone)
        assertTrue(EnginePlan.resolve(lang, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES) is Resolution.Run)
    }

    @Test
    fun everyLanguageHasAnAnswerAtEveryRamSizeAndASmallPhoneNeverGetsABigEngine() {
        for (lang in TranscriptionLanguage.entries) {
            for (ram in listOf(0L, 2_000_000_000L, fourGb, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES - 1, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES, sixGb, 12_000_000_000L)) {
                when (val r = EnginePlan.resolve(lang, ram)) {
                    is Resolution.Run -> assertTrue("${lang.name} @ $ram runs ${r.engine}", r.engine.minRamBytes <= ram)
                    is Resolution.NeedsBiggerPhone -> {
                        assertTrue("${lang.name} @ $ram", ram < EnginePlan.BIG_ENGINE_MIN_RAM_BYTES)
                        assertTrue(r.best.engine.minRamBytes > ram)
                    }
                }
            }
            // forLanguage never throws and never returns an engine too big for the phone
            assertTrue(lang.name, EnginePlan.forLanguage(lang, fourGb).let { it == AsrEngine.VOSK || it.minRamBytes <= fourGb })
        }
    }

    @Test
    fun noEngineAboveTheMemoryLineIsMarkedSmall() {
        // measured peaks on the Mac test (docs/LANGUAGES.md): anything over about 0.7 GB is gated like Whisper small
        listOf(AsrEngine.WHISPER_SMALL, AsrEngine.STARLING, AsrEngine.OMNILINGUAL_300M, AsrEngine.ZIPFORMER_RU, AsrEngine.ZIPFORMER_VI)
            .forEach { assertEquals(it.name, EnginePlan.BIG_ENGINE_MIN_RAM_BYTES, it.minRamBytes) }
        listOf(AsrEngine.VOSK, AsrEngine.NEMO_FAST_CONFORMER, AsrEngine.NEMO_MULTILINGUAL,
            AsrEngine.STREAMING_BN, AsrEngine.DOLPHIN_BASE, AsrEngine.SENSE_VOICE).forEach { assertEquals(it.name, 0L, it.minRamBytes) }
    }

    @Test
    fun fastIsKeptForTheFirstTenAndForcedAccurateOnlyWhereVoskIsMissingOrPoor() {
        firstTen.forEach { assertTrue(it.name, EnginePlan.fastIsUsable(it)) }
        assertFalse(EnginePlan.fastIsUsable(TranscriptionLanguage.ARABIC))
        assertFalse(EnginePlan.fastIsUsable(TranscriptionLanguage.VIETNAMESE)) // Vosk scored 58%
        assertFalse(EnginePlan.fastIsUsable(TranscriptionLanguage.CZECH))      // Vosk scored 60%
        assertTrue(EnginePlan.fastIsUsable(TranscriptionLanguage.PERSIAN))
    }

    @Test
    fun theBarIsAppliedAsWritten() {
        assertEquals(SupportLevel.SUPPORTED, SupportLevel.forError(0.0))
        assertEquals(SupportLevel.SUPPORTED, SupportLevel.forError(30.0))
        assertEquals(SupportLevel.EXPERIMENTAL, SupportLevel.forError(30.1))
        assertEquals(SupportLevel.EXPERIMENTAL, SupportLevel.forError(50.0))
        assertNull(SupportLevel.forError(50.1))
    }

    @Test
    fun noEngineIsOverTheDownloadBudget() {
        // Parakeet v3 (487 MB) was measured and left out: it would serve one language the 375 MB Whisper already offers.
        assertTrue(AsrEngine.entries.none { it.downloadMb > EnginePlan.DOWNLOAD_BUDGET_MB })
        assertTrue(LanguageListing.build(sixGb).filterIsInstance<LanguageRow.Available>().none { it.largeDownload })
    }

    // ---- picker listing -------------------------------------------------------------------------------------------

    @Test
    fun theListingGroupsSupportedThenExperimentalThenBiggerPhoneThenNotYet() {
        val rows = LanguageListing.build(fourGb)
        fun rank(r: LanguageRow) = when (r) {
            is LanguageRow.Available -> if (r.level == SupportLevel.SUPPORTED) 0 else 1
            is LanguageRow.NeedsBiggerPhone -> 2
            is LanguageRow.NotYet -> 3
        }
        assertEquals(rows.map { rank(it) }.sorted(), rows.map { rank(it) })
        assertEquals(TranscriptionLanguage.entries.size + LanguageSupport.NOT_OFFERED.size, rows.size)
        assertTrue(rows.any { it is LanguageRow.NeedsBiggerPhone })
        // on a bigger phone nothing needs one
        assertTrue(LanguageListing.build(sixGb).none { it is LanguageRow.NeedsBiggerPhone })
    }

    @Test
    fun aDowngradedRowSaysSoAndNotOfferedLanguagesNameTheClosestOne() {
        val arabic = LanguageListing.build(fourGb).filterIsInstance<LanguageRow.Available>().first { it.language == TranscriptionLanguage.ARABIC }
        assertTrue(arabic.downgraded)
        assertFalse(LanguageListing.build(sixGb).filterIsInstance<LanguageRow.Available>().first { it.language == TranscriptionLanguage.ARABIC }.downgraded)
        val urdu = LanguageSupport.NOT_OFFERED.first { it.displayName == "Urdu" }
        assertEquals(TranscriptionLanguage.HINDI, urdu.closest)
        assertTrue(urdu.bestErrorPct > EnginePlan.EXPERIMENTAL_MAX_ERROR)
        // a language that is not offered is not in the selectable list
        assertTrue(TranscriptionLanguage.entries.none { it.displayName in LanguageSupport.NOT_OFFERED.map { n -> n.displayName } })
    }

    // ---- word timing ----------------------------------------------------------------------------------------------

    @Test
    fun onlyWhisperHasNoWordTimesAndWhisperGetsTheSpeechSpreadTiming() {
        assertEquals(listOf(AsrEngine.WHISPER_SMALL), AsrEngine.entries.filter { !it.wordTimes })
        // distributeOverSpeech is what WordTiming gives Whisper words; it must keep working on Arabic and Thai pieces
        val spans = listOf(SpeechSpan(0, 2000))
        val arabic = distributeOverSpeech(listOf("مرحبا", "بكم", "في", "البرنامج"), spans, 1000)
        assertEquals(4, arabic.size)
        assertTrue(arabic.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        assertTrue(arabic.first().startMs >= 1000 && arabic.last().endMs <= 3000)
    }

    @Test
    fun theVietnameseEngineIsTheOnlyOneThatWritesCapitals() {
        assertEquals(listOf(AsrEngine.ZIPFORMER_VI), AsrEngine.entries.filter { it.upperCaseOutput })
    }

    @Test
    fun generatedTableMentionsNoLanguageTwice() {
        val src = File("src/main/kotlin/com/kreativekoala/echonote/service/LanguageSupport.kt").readText()
        val names = Regex("TranscriptionLanguage\\.([A-Z]+) to listOf").findAll(src).map { it.groupValues[1] }.toList()
        assertEquals(names.size, names.toSet().size)
        assertEquals(TranscriptionLanguage.entries.map { it.name }.toSet(), names.toSet())
    }
}
