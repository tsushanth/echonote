package com.kreativekoala.echonote.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLimiterTest {
    private var clock = 1_000_000_000L
    private var saved: String? = null
    private fun limiter() = ReportLimiter({ saved }, { saved = it }, { clock })

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun theSameFailureIsSentOnceAnHourEvenWhenOnlyNumbersDiffer() {
        val l = limiter()
        assertTrue(l.allow(ReportFlow.MODEL_DOWNLOAD.code, "http_status 503"))
        assertFalse(l.allow(ReportFlow.MODEL_DOWNLOAD.code, "http_status 503"))
        assertFalse(l.allow(ReportFlow.MODEL_DOWNLOAD.code, "http_status 404"))
        clock += hour
        assertTrue(l.allow(ReportFlow.MODEL_DOWNLOAD.code, "http_status 503"))
    }

    @Test
    fun oneFlowIsCappedPerHour() {
        val l = limiter()
        assertEquals(3, listOf("a", "b", "c", "d", "e").count { l.allow(ReportFlow.TRANSCRIPTION_RUN.code, it) })
        assertTrue(l.allow(ReportFlow.RECORDING_START.code, "a"))
    }

    @Test
    fun everythingTogetherIsCappedPerMinute() {
        val l = limiter()
        assertEquals(10, ReportFlow.entries.count { l.allow(it.code, "x") })
        clock += minute
        assertTrue(l.allow(ReportFlow.entries.last().code, "y"))
    }

    @Test
    fun theDailyCapStopsFailuresButNotCrashes() {
        val l = limiter()
        var sent = 0
        repeat(20) { round ->
            for (flow in listOf(ReportFlow.TRANSCRIPTION_RUN.code, ReportFlow.RECORDING_START.code, ReportFlow.MODEL_DOWNLOAD.code)) {
                if (l.allow(flow, "round-${'a' + round}")) sent++
            }
            clock += hour
        }
        assertEquals(30, sent)
        assertTrue(l.allow(ReportFlow.CRASH.code, "boom", crash = true))
        clock += 24 * hour
        assertTrue(l.allow(ReportFlow.TRANSCRIPTION_RUN.code, "next day"))
    }

    @Test
    fun theLimitsSurviveARestart() {
        assertTrue(limiter().allow(ReportFlow.CRASH.code, "boom", crash = true))
        assertFalse(limiter().allow(ReportFlow.CRASH.code, "boom", crash = true))
    }

    @Test
    fun aClockMovedBackOrACorruptHistoryDoesNotBlockReporting() {
        assertTrue(limiter().allow(ReportFlow.CRASH.code, "boom"))
        clock -= 48 * hour
        assertTrue(limiter().allow(ReportFlow.CRASH.code, "boom"))
        saved = "garbage\n1,2\nx,y,z"
        assertTrue(limiter().allow(ReportFlow.CRASH.code, "boom"))
    }
}
