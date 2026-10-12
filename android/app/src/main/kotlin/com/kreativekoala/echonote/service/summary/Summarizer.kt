package com.kreativekoala.echonote.service.summary

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** How a summary was produced. The UI labels each kind differently and honestly. */
enum class SummaryKind {
    /** Sentences picked from the transcript by a deterministic algorithm. Not written text. */
    EXTRACTIVE,

    /** Text written by an on-device language model (Gemini Nano through ML Kit GenAI). */
    ON_DEVICE_AI
}

/** A finished summary: short points, in the order they appear in the transcript for EXTRACTIVE. */
data class SummaryResult(val points: List<String>, val kind: SummaryKind) {
    fun asPlainText(): String = points.joinToString("\n") { "- $it" }
}

interface Summarizer {
    val kind: SummaryKind

    /**
     * Summarizes [text]. [segments] are the timed words of the same transcript, when
     * known; they let a summarizer cope with text that has no punctuation.
     * Returns null when the text is too short to be worth summarizing, or when this
     * summarizer cannot do it on this device (the caller then tries the next one).
     * Must never send the text off the device.
     */
    suspend fun summarize(
        text: String,
        language: TranscriptionLanguage,
        segments: List<TranscriptSegment> = emptyList()
    ): SummaryResult?
}
