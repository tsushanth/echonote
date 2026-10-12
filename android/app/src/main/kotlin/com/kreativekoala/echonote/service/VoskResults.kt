package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionOutput
import org.json.JSONObject

/**
 * Merges the JSON results a Vosk recognizer returns over a whole recording: one per finished sentence
 * (from `result`, each time `acceptWaveForm` reports an end of speech) plus the last one (`finalResult`).
 * Vosk's word times are continuous across sentences, so they are used as they are. Reading only the final
 * result keeps just the text after the last pause, which lost most of a recording.
 * Returns null when nothing was recognized.
 */
fun mergeVoskResults(resultJsons: List<String>): TranscriptionOutput? {
    val words = mutableListOf<TranscriptSegment>()
    val loose = mutableListOf<String>() // text from results that carry no per-word detail
    for (raw in resultJsons) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: continue
        val arr = json.optJSONArray("result")
        if (arr != null && arr.length() > 0) {
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                val word = w.optString("word")
                if (word.isBlank()) continue
                words += TranscriptSegment(
                    word = word,
                    startMs = (w.optDouble("start", 0.0) * 1000).toLong(),
                    endMs = (w.optDouble("end", 0.0) * 1000).toLong()
                )
            }
        } else {
            json.optString("text", "").trim().takeIf { it.isNotEmpty() }?.let { loose += it }
        }
    }
    val text = if (words.isNotEmpty() && loose.isEmpty()) joinWords(words.map { it.word })
    else (listOf(joinWords(words.map { it.word })).filter { it.isNotEmpty() } + loose).joinToString(" ")
    return if (text.isBlank()) null else TranscriptionOutput(text, words)
}
