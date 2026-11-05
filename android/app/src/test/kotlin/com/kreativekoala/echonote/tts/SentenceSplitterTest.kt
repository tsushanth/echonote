package com.kreativekoala.echonote.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {
    @Test fun splitsOnTerminatorsFollowedBySpace() {
        assertEquals(listOf("Hello there.", "How are you?", "Fine!"), SentenceSplitter.split("Hello there. How are you? Fine!"))
    }

    @Test fun doesNotSplitInsideNumbersOrDomains() {
        assertEquals(listOf("The rate was 3.5 percent today."), SentenceSplitter.split("The rate was 3.5 percent today."))
    }

    @Test fun keepsClosingQuotesAndRunsOfPunctuationTogether() {
        assertEquals(listOf("She said \"stop.\"", "Then left?!", "Wait..."), SentenceSplitter.split("She said \"stop.\" Then left?! Wait..."))
    }

    @Test fun newlinesSeparateItemsEvenWithoutPunctuation() {
        assertEquals(listOf("First point", "Second point"), SentenceSplitter.split("First point\nSecond point"))
    }

    @Test fun cjkTerminatorsNeedNoFollowingSpace() {
        assertEquals(listOf("你好。", "再见！"), SentenceSplitter.split("你好。再见！"))
    }

    @Test fun trailingTextWithoutTerminatorIsKept() {
        assertEquals(listOf("One.", "and then"), SentenceSplitter.split("One. and then"))
    }

    @Test fun blankInputGivesNothing() {
        assertTrue(SentenceSplitter.split("  \n \n").isEmpty())
    }

    @Test fun longSentencesAreCutAtCommasUnderTheLimit() {
        val long = (1..200).joinToString(", ") { "word$it" }
        val parts = SentenceSplitter.split("$long.")
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= SentenceSplitter.MAX_ITEM_CHARS })
        assertEquals(long.replace(" ", ""), parts.joinToString("").replace(" ", "").removeSuffix("."))
    }

    @Test fun anUnbrokenHugeWordIsHardCut() {
        val parts = SentenceSplitter.limitLength("x".repeat(1_300))
        assertEquals(listOf(500, 500, 300), parts.map { it.length })
    }

    @Test fun listenPlanMapsPiecesBackToTheirUnit() {
        val long = (1..200).joinToString(", ") { "w$it" }
        val plan = ListenPlan.of(listOf("Short one.", long, "Last."))
        assertEquals("Short one.", plan.pieces.first())
        assertEquals(0, plan.unitAt(0))
        val lastPiece = plan.pieces.lastIndex
        assertEquals(2, plan.unitAt(lastPiece))
        assertEquals(1, plan.unitAt(1))
        assertEquals(1, plan.unitAt(2))
        assertEquals(1, plan.firstPieceOf(1))
        assertEquals(lastPiece, plan.firstPieceOf(2))
        assertEquals(-1, plan.unitAt(99))
    }
}
