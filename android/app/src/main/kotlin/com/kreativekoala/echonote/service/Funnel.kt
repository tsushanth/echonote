package com.kreativekoala.echonote.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.kreativekoala.echonote.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Privacy-conscious activation funnel. Every event carries only a fixed event name and, at most, a
 * coarse bucket or a fixed error code. Never audio, transcript text, titles, file names, paths or
 * free-text error messages. Documented in docs/metrics.md.
 *
 * The app is completely free (since 1.9.3), so there are no paywall/purchase events: the funnel is
 * open -> first recording -> first finished transcript -> share/export, which is where a future
 * monetisation decision would have to be measured.
 */
interface FunnelSink {
    fun log(name: String, params: Map<String, String> = emptyMap())
}

/** Remembers which one-time ("first_*") events were already sent, across launches. */
interface OnceStore {
    /** True exactly once per [key]: the first call records it and returns true. */
    fun firstTime(key: String): Boolean
}

class FunnelTracker(private val sink: FunnelSink, private val once: OnceStore) {

    fun recordingStarted() {
        if (once.firstTime("recording_started")) sink.log("first_recording_started")
        sink.log("recording_started")
    }

    fun recordingCompleted(durationMs: Long) {
        val bucket = durationBucket(durationMs)
        if (once.firstTime("recording_completed")) sink.log("first_recording_completed", mapOf("duration" to bucket))
        sink.log("recording_completed", mapOf("duration" to bucket))
    }

    fun transcriptionStarted() = sink.log("transcription_started")

    fun transcriptionCompleted() {
        if (once.firstTime("transcription_completed")) sink.log("first_transcription_completed")
        sink.log("transcription_completed")
    }

    fun transcriptionFailed(message: String) =
        sink.log("transcription_failed", mapOf("error_code" to errorCode(message)))

    /** [what] is one of "recording", "transcript". */
    fun shared(what: String) {
        if (once.firstTime("shared")) sink.log("first_share", mapOf("what" to what))
        sink.log("share", mapOf("what" to what))
    }

    companion object {
        fun durationBucket(ms: Long): String = when {
            ms < 10_000 -> "lt_10s"
            ms < 60_000 -> "10s_1m"
            ms < 300_000 -> "1_5m"
            ms < 1_800_000 -> "5_30m"
            else -> "gt_30m"
        }

        /** Maps a user-facing error message to a fixed code; the message itself is never sent. */
        fun errorCode(message: String): String {
            val m = message.lowercase()
            return when {
                "not found" in m -> "file_missing"
                "no speech" in m -> "no_speech"
                "no audio" in m -> "no_audio"
                "not enough memory" in m -> "low_memory"
                "download" in m -> "model_download"
                "fast engine" in m -> "engine_unavailable"
                else -> "other"
            }
        }
    }
}

private class FirebaseSink : FunnelSink {
    private val analytics: FirebaseAnalytics? = try { Firebase.analytics } catch (_: Exception) { null }
    override fun log(name: String, params: Map<String, String>) {
        if (BuildConfig.DEBUG) Log.d("Funnel", "$name $params")
        try {
            val bundle = if (params.isEmpty()) null else Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
            analytics?.logEvent(name, bundle)
        } catch (_: Exception) { /* analytics must never break the app */ }
    }
}

private class PrefsOnceStore(context: Context) : OnceStore {
    private val prefs = context.getSharedPreferences("funnel_once", Context.MODE_PRIVATE)
    @Synchronized
    override fun firstTime(key: String): Boolean {
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }
}

/** Process-wide tracker; a no-op until [init] runs so tests and previews never touch Firebase. */
object Funnel {
    @Volatile private var tracker: FunnelTracker? = null

    fun init(context: Context) {
        tracker = FunnelTracker(FirebaseSink(), PrefsOnceStore(context.applicationContext))
    }

    fun recordingStarted() { tracker?.recordingStarted() }
    fun recordingCompleted(durationMs: Long) { tracker?.recordingCompleted(durationMs) }
    fun transcriptionStarted() { tracker?.transcriptionStarted() }
    fun transcriptionCompleted() { tracker?.transcriptionCompleted() }
    fun transcriptionFailed(message: String) { tracker?.transcriptionFailed(message) }
    fun shared(what: String) { tracker?.shared(what) }
}
