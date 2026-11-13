package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker's search box. */
class LanguageListingFilterTest {
    private val sixGb = 5_700_000_000L
    private val rows = LanguageListing.build(sixGb)

    private fun names(q: String) = LanguageListing.filter(rows, q).map { it.sortName }

    @Test
    fun aBlankQueryKeepsEveryRowInOrder() {
        assertEquals(rows, LanguageListing.filter(rows, ""))
        assertEquals(rows, LanguageListing.filter(rows, "   "))
    }

    @Test
    fun matchesTheEnglishNameIgnoringCase() {
        assertEquals(listOf("Portuguese"), names("portu"))
        assertTrue(names("PAN").contains("Spanish"))
    }

    @Test
    fun matchesTheNativeNameInItsOwnScript() {
        assertEquals(listOf("Hindi"), names("हिन्दी"))
        assertEquals(listOf("Russian"), names("Русский"))
        assertEquals(listOf("Spanish"), names("español"))
    }

    @Test
    fun findsLanguagesThatAreNotOfferedToo() {
        assertEquals(listOf("Urdu"), names("urdu"))
        assertEquals(listOf("Hebrew"), names("עברית"))
    }

    @Test
    fun noMatchGivesAnEmptyList() {
        assertEquals(emptyList<String>(), names("zzzz"))
    }

    @Test
    fun theFilteredRowsKeepTheirGroupOrder() {
        val got = LanguageListing.filter(rows, "an")
        assertEquals(got, rows.filter { it in got })
        assertTrue(got.any { it is LanguageRow.Available && it.language == TranscriptionLanguage.GERMAN })
    }
}
