package com.kreativekoala.echonote.ui

import com.kreativekoala.echonote.ui.settings.parseLicenseBlocks
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesTest {
    private val licenseFile = File("src/main/res/raw/open_source_licenses.txt")

    @Test
    fun blocksSplitOnBlankLinesAndHeadingsAreDetected() {
        val blocks = parseLicenseBlocks("intro line\n\n\n=== MIT License ===\n(applies to x)\n\nbody one\nbody two\n")
        assertEquals(listOf("intro line", "=== MIT License ===\n(applies to x)", "body one body two"), blocks.map { it.text })
        assertEquals(listOf(false, true, false), blocks.map { it.isHeading })
    }

    @Test
    fun paragraphsAreReflowedWithoutChangingTheWords() {
        val text = parseLicenseBlocks("   \"Work\" shall mean the work of\n      authorship, whether in\n   Source form.").single().text
        assertEquals("\"Work\" shall mean the work of authorship, whether in Source form.", text)
    }

    @Test
    fun aParagraphThatMerelyStartsWithEqualsIsNotAHeading() {
        assertEquals(false, parseLicenseBlocks("=== not closed").single().isHeading)
    }

    @Test
    fun bundledFileCreditsEveryShippedComponentAndHasTheFullTexts() {
        val text = licenseFile.readText()
        listOf(
            "Whisper", "OpenAI", "sherpa-onnx", "ONNX Runtime", "eSpeak NG", "Jonathan Duddington", "Microsoft Corporation", "pyannote", "CNRS",
            "TitaNet", "NVIDIA", "FastConformer", "Commons Compress", "CC BY 4.0", "Vosk", "Figtree", "Source Serif 4"
        ).forEach { assertTrue("missing credit: $it", text.contains(it)) }
        // the three full license texts, not just names
        assertTrue(text.contains("=== Apache License 2.0 ==="))
        assertTrue(text.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        assertTrue(text.contains("=== MIT License ==="))
        assertTrue(text.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        assertTrue(text.contains("=== SIL Open Font License 1.1 ==="))
        assertTrue(text.contains("PERMISSION & CONDITIONS"))
    }

    @Test
    fun eSpeakNgCreditNamesItsLicenseAndPointsToSourceAndLicenseText() {
        val text = licenseFile.readText()
        assertTrue(text.contains("GPL-3.0-or-later"))
        assertTrue(text.contains("https://github.com/espeak-ng/espeak-ng"))
        assertTrue(text.contains("https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1"))
        assertTrue(text.contains("https://www.gnu.org/licenses/gpl-3.0.txt"))
    }
}
