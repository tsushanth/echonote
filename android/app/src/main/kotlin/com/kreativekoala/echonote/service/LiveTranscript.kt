package com.kreativekoala.echonote.service

import org.json.JSONObject

/** Reads the text out of a Vosk partialResult ("partial") or result ("text") JSON string. Never throws. */
fun parseVoskText(json: String, key: String): String =
    try { JSONObject(json).optString(key, "").trim() } catch (_: Exception) { "" }

/**
 * Vosk's Chinese and Japanese models put a space between every token. Drop spaces that sit between two
 * characters of a script written without spaces; keep spaces everywhere else.
 */
fun normalizeSpacing(s: String): String {
    val t = s.trim()
    if (t.none { it == ' ' }) return t
    return buildString {
        for (i in t.indices) {
            val c = t[i]
            if (c == ' ') {
                val prev = t.take(i).trimEnd().lastOrNull()
                val next = t.drop(i).trimStart().firstOrNull()
                if (prev != null && next != null && isNoSpaceScript(prev) && isNoSpaceScript(next)) continue
                if (isNotEmpty() && last() == ' ') continue
            }
            append(c)
        }
    }
}

/**
 * Merges Vosk results into the text shown while recording: finished utterances followed by the
 * in-progress partial. Only the last [maxChars] characters are shown, so a long recording stays cheap
 * to render and the view always shows what was said most recently. Not thread-safe; use from one thread.
 */
class LiveTranscriptAccumulator(private val maxChars: Int = 400) {
    private var committed = ""
    private var partial = ""

    fun onPartial(text: String) {
        partial = normalizeSpacing(text)
    }

    fun onFinal(text: String) {
        val t = normalizeSpacing(text)
        partial = ""
        if (t.isEmpty()) return
        committed = if (committed.isEmpty()) t else joinWords(listOf(committed, t))
        // Keep memory flat: nothing older than twice the window can ever be shown again.
        if (committed.length > maxChars * 2) {
            var cut = committed.length - maxChars * 2
            if (Character.isLowSurrogate(committed[cut])) cut++
            committed = committed.substring(cut)
        }
    }

    fun clear() {
        committed = ""
        partial = ""
    }

    fun text(): String {
        val all = when {
            committed.isEmpty() -> partial
            partial.isEmpty() -> committed
            else -> joinWords(listOf(committed, partial))
        }
        if (all.length <= maxChars) return all
        var start = all.length - maxChars
        if (Character.isLowSurrogate(all[start])) start++
        // Avoid starting mid-word for spaced scripts: move to the next space if one is close.
        if (!isNoSpaceScript(all[start]) && all[start - 1] != ' ') {
            val sp = all.indexOf(' ', start)
            if (sp in start..(start + 30)) start = sp + 1
        }
        return "…" + all.substring(start).trimStart()
    }
}
