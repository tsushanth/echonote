package com.kreativekoala.echonote.util

/**
 * Helpers for right-to-left transcripts (Arabic, Persian, Urdu, Hebrew).
 *
 * The on-screen transcript is a single Compose text per paragraph, and Compose picks each paragraph's
 * direction from its first strong character, so the screen needs nothing. Exported subtitle files are read by
 * other players: a cue that starts with a digit or a Latin word ("WhatsApp رسالة") would be laid out left to
 * right there, so every cue that contains right-to-left letters is prefixed with a right-to-left mark.
 */
object Bidi {
    const val RLM = '\u200F'

    private fun isRtl(cp: Int): Boolean = when (Character.getDirectionality(cp)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        else -> false
    }

    /** True when [text] contains at least one right-to-left letter. */
    fun containsRtl(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isRtl(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** [text] prefixed with a right-to-left mark when it contains right-to-left letters; otherwise unchanged. */
    fun markRtl(text: String): String =
        if (text.isNotEmpty() && text[0] != RLM && containsRtl(text)) RLM + text else text
}
