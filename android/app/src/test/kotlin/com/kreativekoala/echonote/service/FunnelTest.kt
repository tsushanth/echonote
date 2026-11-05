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
}
