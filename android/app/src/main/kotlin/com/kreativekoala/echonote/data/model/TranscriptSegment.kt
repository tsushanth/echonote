package com.kreativekoala.echonote.data.model

import org.json.JSONArray
import org.json.JSONObject

data class TranscriptSegment(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

data class TranscriptionOutput(
    val text: String,
    val segments: List<TranscriptSegment>
)

fun List<TranscriptSegment>.toJson(): String {
    val arr = JSONArray()
    forEach { seg ->
        val obj = JSONObject()
        obj.put("word", seg.word)
        obj.put("startMs", seg.startMs)
        obj.put("endMs", seg.endMs)
        arr.put(obj)
    }
    return arr.toString()
}

fun String.toTranscriptSegments(): List<TranscriptSegment> {
    return try {
        val arr = JSONArray(this)
        val result = mutableListOf<TranscriptSegment>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                TranscriptSegment(
                    word = obj.getString("word"),
                    startMs = obj.getLong("startMs"),
                    endMs = obj.getLong("endMs")
                )
            )
        }
        result
    } catch (_: Exception) {
        emptyList()
    }
}
