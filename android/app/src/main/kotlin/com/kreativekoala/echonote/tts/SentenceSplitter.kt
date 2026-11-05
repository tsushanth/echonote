package com.kreativekoala.echonote.tts

/**
 * Splits text into utterance-sized sentences for reading aloud and highlighting.
 * Pure Kotlin so it can be unit tested without Android.
 */
object SentenceSplitter {

    /** Longest item we hand to a TTS engine; longer sentences are cut at a comma or space. */
    const val MAX_ITEM_CHARS = 500

    private val terminators = setOf('.', '!', '?', '…', '。', '！', '？', '।')
    private val closers = setOf('"', '\'', '”', '’', ')', ']', '」', '』')

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                flush(sb, out); i++; continue
            }
            sb.append(c)
            if (c in terminators) {
                // swallow runs like "?!" or "..." and closing quotes
                while (i + 1 < text.length && (text[i + 1] in terminators || text[i + 1] in closers)) { sb.append(text[++i]) }
                val atEnd = i + 1 >= text.length
                val next = if (atEnd) ' ' else text[i + 1]
                // CJK terminators have no following space; a period inside "3.5" or "e.g.x" is not a sentence end.
                val cjk = c == '。' || c == '！' || c == '？'
                if (atEnd || next.isWhitespace() || cjk) flush(sb, out)
            }
            i++
        }
        flush(sb, out)
        return out.flatMap { limitLength(it) }
    }

    /** Cuts [item] into pieces of at most [max] chars at a comma, else a space, else hard. */
    fun limitLength(item: String, max: Int = MAX_ITEM_CHARS): List<String> {
        val s = item.trim()
        if (s.length <= max) return if (s.isEmpty()) emptyList() else listOf(s)
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOfAny(charArrayOf(',', ';', ':', '，', '；'), max - 1)
            if (cut < max / 3) cut = rest.lastIndexOf(' ', max - 1)
            if (cut < max / 3) cut = max - 1
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun flush(sb: StringBuilder, out: MutableList<String>) {
        val s = sb.toString().trim()
        if (s.isNotEmpty()) out += s
        sb.setLength(0)
    }
}

/**
 * What is actually queued for speech: each [units] entry (a key point, a sentence, a passage) becomes one or more
 * pieces of limited length. [unitOfPiece] maps a piece back to its unit so the UI can highlight the right row.
 */
class ListenPlan private constructor(val pieces: List<String>, val unitOfPiece: IntArray) {

    fun unitAt(pieceIndex: Int): Int = if (pieceIndex in unitOfPiece.indices) unitOfPiece[pieceIndex] else -1

    /** Index of the first piece of [unit], or -1. */
    fun firstPieceOf(unit: Int): Int = unitOfPiece.indexOfFirst { it == unit }

    companion object {
        fun of(units: List<String>): ListenPlan {
            val pieces = mutableListOf<String>()
            val map = mutableListOf<Int>()
            units.forEachIndexed { u, text ->
                SentenceSplitter.limitLength(text).forEach { pieces += it; map += u }
            }
            return ListenPlan(pieces, map.toIntArray())
        }
    }
}
