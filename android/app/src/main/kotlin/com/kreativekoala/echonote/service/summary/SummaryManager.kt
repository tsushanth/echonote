package com.kreativekoala.echonote.service.summary

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the playback screen shows for the summary card. */
sealed interface SummaryUiState {
    /** Nothing asked for yet (and nothing stored). */
    data object None : SummaryUiState
    data object Working : SummaryUiState
    data class Ready(val result: SummaryResult, val stale: Boolean) : SummaryUiState
    /** The transcript is too short to be worth summarizing. */
    data object TooShort : SummaryUiState
}

/**
 * Chooses a summarizer, runs it, and stores the answer. Today that is the extractive one only; the
 * `ai` slot in [summarize] is kept so an on-device model can be added later (see docs/SUMMARIES.md),
 * with any null or failure falling back to extractive.
 */
@Singleton
class SummaryManager @Inject constructor(
    private val store: SummaryStore,
    private val extractive: ExtractiveSummarizer
) {
    suspend fun loadStored(recordingId: String, transcript: String?): SummaryUiState = withContext(Dispatchers.IO) {
        val stored = store.load(recordingId) ?: return@withContext SummaryUiState.None
        if (transcript.isNullOrBlank()) SummaryUiState.None
        else SummaryUiState.Ready(stored.result, stale = stored.isStaleFor(transcript))
    }

    suspend fun generate(
        recordingId: String,
        transcript: String,
        language: TranscriptionLanguage,
        segments: List<TranscriptSegment>
    ): SummaryUiState {
        val result = summarize(extractive, null, transcript, language, segments)
            ?: return SummaryUiState.TooShort
        withContext(Dispatchers.IO) {
            store.save(recordingId, StoredSummary(result, hashTranscript(transcript), System.currentTimeMillis()))
        }
        return SummaryUiState.Ready(result, stale = false)
    }

    fun delete(recordingId: String) = store.delete(recordingId)
}

/** Fail-safe chain: the AI summarizer's null or exception both fall through to [fallback]. */
internal suspend fun summarize(
    fallback: Summarizer,
    ai: Summarizer?,
    transcript: String,
    language: TranscriptionLanguage,
    segments: List<TranscriptSegment>
): SummaryResult? {
    if (ai != null) {
        val viaAi = try {
            ai.summarize(transcript, language, segments)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (viaAi != null) return viaAi
    }
    return fallback.summarize(transcript, language, segments)
}
