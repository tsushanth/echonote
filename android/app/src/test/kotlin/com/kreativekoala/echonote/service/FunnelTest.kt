package com.kreativekoala.echonote.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunnelTest {
    private class Sink : FunnelSink {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        override fun log(name: String, params: Map<String, String>) { events += name to params }
    }
    private class Once : OnceStore {
        private val seen = mutableSetOf<String>()
        override fun firstTime(key: String) = seen.add(key)
    }

    @Test fun firstEventsFireOnce() {
        val s = Sink(); val t = FunnelTracker(s, Once())
        t.recordingStarted(); t.recordingStarted()
        t.recordingCompleted(5_000); t.recordingCompleted(70_000)
        t.transcriptionStarted(); t.transcriptionCompleted(); t.transcriptionCompleted()
        val names = s.events.map { it.first }
        assertEquals(1, names.count { it == "first_recording_started" })
        assertEquals(2, names.count { it == "recording_started" })
        assertEquals(1, names.count { it == "first_recording_completed" })
        assertEquals(1, names.count { it == "first_transcription_completed" })
        assertEquals(2, names.count { it == "transcription_completed" })
        assertTrue(names.indexOf("first_recording_started") < names.indexOf("recording_started"))
    }

    @Test fun durationBuckets() {
        assertEquals("lt_10s", FunnelTracker.durationBucket(9_999))
        assertEquals("10s_1m", FunnelTracker.durationBucket(10_000))
        assertEquals("1_5m", FunnelTracker.durationBucket(60_000))
        assertEquals("5_30m", FunnelTracker.durationBucket(300_000))
        assertEquals("gt_30m", FunnelTracker.durationBucket(1_800_000))
    }

    @Test fun errorsAreMappedToFixedCodesAndNeverForwardedVerbatim() {
        val msgs = listOf(
            "Recording file not found" to "file_missing",
            "No speech detected in this recording" to "no_speech",
            "No audio found in this recording" to "no_audio",
            "Not enough memory for the accurate model on this phone. Switch Transcription Quality to Fast." to "low_memory",
            "Could not download the accurate speech model. Check your internet connection and try again. (java.net.UnknownHostException: x)" to "model_download",
            "Transcription failed: /storage/emulated/0/secret-name.m4a exploded" to "other",
        )
        for ((m, code) in msgs) {
            val s = Sink(); FunnelTracker(s, Once()).transcriptionFailed(m)
            assertEquals(mapOf("error_code" to code), s.events.single().second)
            assertFalse(s.events.single().second.values.any { it.contains("/") || it.contains(" ") })
        }
    }

    @Test fun sharedCarriesOnlyTheKind() {
        val s = Sink(); FunnelTracker(s, Once()).shared("transcript")
        assertEquals(listOf("first_share", "share"), s.events.map { it.first })
        assertEquals(mapOf("what" to "transcript"), s.events.first().second)
    }

    @Test fun listenEventsCarryOnlyAWhitelistedSource() {
        val s = Sink(); val t = FunnelTracker(s, Once())
        t.listenStarted("summary"); t.listenCompleted("passage"); t.listenStarted("My Secret Title")
        assertEquals(listOf("listen_started", "listen_completed", "listen_started"), s.events.map { it.first })
        assertEquals(mapOf("source" to "summary"), s.events[0].second)
        assertEquals(mapOf("source" to "passage"), s.events[1].second)
        assertEquals(mapOf("source" to "other"), s.events[2].second)
    }

    @Test fun findAndAskEventsCarryInputKindAndCountBucketNeverText() {
        val s = Sink(); val t = FunnelTracker(s, Once())
        t.findQuery("typed", 0); t.findQuery("voice", 3); t.askQuery("typed", 12); t.askQuery("what did Maria say about the loan", 99)
        t.findResultOpened("all"); t.askResultOpened("recording"); t.findResultOpened("some title")
        assertEquals(mapOf("input" to "typed", "results" to "0"), s.events[0].second)
        assertEquals(mapOf("input" to "voice", "results" to "2_5"), s.events[1].second)
        assertEquals(mapOf("input" to "typed", "results" to "6_20"), s.events[2].second)
        assertEquals(mapOf("input" to "other", "results" to "gt_20"), s.events[3].second)
        assertEquals(mapOf("scope" to "all"), s.events[4].second)
        assertEquals(mapOf("scope" to "recording"), s.events[5].second)
        assertEquals(mapOf("scope" to "other"), s.events[6].second)
        assertEquals(listOf("find_query", "find_query", "ask_query", "ask_query", "find_result_opened", "ask_result_opened", "find_result_opened"), s.events.map { it.first })
    }

    @Test fun countBuckets() {
        assertEquals("0", FunnelTracker.countBucket(0)); assertEquals("1", FunnelTracker.countBucket(1))
        assertEquals("2_5", FunnelTracker.countBucket(2)); assertEquals("2_5", FunnelTracker.countBucket(5))
        assertEquals("6_20", FunnelTracker.countBucket(6)); assertEquals("6_20", FunnelTracker.countBucket(20))
        assertEquals("gt_20", FunnelTracker.countBucket(21))
    }

    @Test fun premiumEventsHaveNoParams() {
        val s = Sink(); val t = FunnelTracker(s, Once())
        t.premiumRowViewed(); t.premiumSheetOpened()
        assertEquals(listOf("premium_row_viewed", "premium_sheet_opened"), s.events.map { it.first })
        assertTrue(s.events.all { it.second.isEmpty() })
    }
}
