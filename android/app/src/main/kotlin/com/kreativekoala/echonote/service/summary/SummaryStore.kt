package com.kreativekoala.echonote.service.summary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A summary saved next to the app's data, tied to the exact transcript it was made from. */
data class StoredSummary(
    val result: SummaryResult,
    /** SHA-256 of the transcript text the summary was computed from. */
    val transcriptHash: String,
    val createdAt: Long
) {
    fun isStaleFor(transcript: String): Boolean = transcriptHash != hashTranscript(transcript)
}

fun hashTranscript(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Sidecar storage: one small JSON file per recording under filesDir/summaries, so the Room
 * schema is untouched (no migration risk for existing users). Files are removed when the
 * recording is permanently deleted.
 */
class SummaryFiles(private val dir: File) {
    private fun fileFor(recordingId: String): File {
        // Recording ids are UUIDs; refuse anything that could escape the folder.
        require(recordingId.isNotEmpty() && recordingId.none { it == '/' || it == '\\' || it == '.' }) { "bad id" }
        return File(dir, "$recordingId.json")
    }

    fun load(recordingId: String): StoredSummary? = try {
        val f = fileFor(recordingId)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("points")
            val points = (0 until arr.length()).map { arr.getString(it) }
            val kind = SummaryKind.valueOf(o.getString("kind"))
            if (points.isEmpty()) null
            else StoredSummary(SummaryResult(points, kind), o.getString("hash"), o.optLong("createdAt"))
        }
    } catch (_: Exception) {
        null
    }

    fun save(recordingId: String, summary: StoredSummary) {
        try {
            dir.mkdirs()
            val o = JSONObject()
                .put("v", 1)
                .put("kind", summary.result.kind.name)
                .put("points", JSONArray(summary.result.points))
                .put("hash", summary.transcriptHash)
                .put("createdAt", summary.createdAt)
            val target = fileFor(recordingId)
            val tmp = File(dir, "$recordingId.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        } catch (_: Exception) {
            // A summary is a convenience; failing to save it must never break the screen.
        }
    }

    fun delete(recordingId: String) {
        try { fileFor(recordingId).delete() } catch (_: Exception) {}
    }

    /** Removes summaries whose recording no longer exists. */
    fun pruneExcept(liveIds: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.extension == "json" && f.nameWithoutExtension !in liveIds) f.delete()
            else if (f.extension == "tmp") f.delete()
        }
    }
}

@Singleton
class SummaryStore @Inject constructor(@ApplicationContext context: Context) {
    private val files = SummaryFiles(File(context.filesDir, "summaries"))
    fun load(recordingId: String) = files.load(recordingId)
    fun save(recordingId: String, summary: StoredSummary) = files.save(recordingId, summary)
    fun delete(recordingId: String) = files.delete(recordingId)
    fun pruneExcept(liveIds: Set<String>) = files.pruneExcept(liveIds)
}
