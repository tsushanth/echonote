package com.kreativekoala.echonote.service.summary

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gemini Nano through Google's ML Kit GenAI Summarization API (beta). Runs inside the
 * system's AICore service on the phone; the text never leaves the device.
 *
 * UNVERIFIED: this was compiled against the real library but has never been run on a phone.
 * It is OFF ([ENABLED] = false). Before turning it on, check on the Pixel 9 Pro what is listed
 * in docs/SUMMARIES.md. Every failure path returns null so the caller falls back to
 * [ExtractiveSummarizer].
 *
 * Limits of the API: English, Japanese and Korean only; input under about 4,000 tokens
 * (longer input is truncated by the library, so only the start of a long recording is
 * summarized); needs a supported device and a locked bootloader. This class never triggers the
 * model download: it only runs when the system already reports the feature AVAILABLE.
 */
@Singleton
class OnDeviceAiSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) : Summarizer {
    override val kind = SummaryKind.ON_DEVICE_AI

    override suspend fun summarize(
        text: String,
        language: TranscriptionLanguage,
        segments: List<TranscriptSegment>
    ): SummaryResult? {
        if (!ENABLED) return null
        val lang = mlKitLanguage(language) ?: return null
        if (text.trim().length < MIN_CHARS) return null
        val summarizer = try {
            Summarization.getClient(
                SummarizerOptions.builder(context)
                    .setInputType(SummarizerOptions.InputType.ARTICLE)
                    .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                    .setLanguage(lang)
                    .setLongInputAutoTruncationEnabled(true)
                    .build()
            )
        } catch (_: Throwable) {
            return null
        }
        return try {
            val status = withTimeoutOrNull(STATUS_TIMEOUT_MS) { summarizer.checkFeatureStatus().await() }
            if (status != FeatureStatus.AVAILABLE) return null
            val request = SummarizationRequest.builder(text.trim()).build()
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { summarizer.runInference(request).await() }
                ?: return null
            val points = parseBullets(result.summary)
            if (points.isEmpty()) null else SummaryResult(points, SummaryKind.ON_DEVICE_AI)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } finally {
            try { summarizer.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        /** Flip to true only after the on-device checks in docs/SUMMARIES.md pass. */
        const val ENABLED = false

        private const val MIN_CHARS = 400
        private const val STATUS_TIMEOUT_MS = 5_000L
        private const val INFERENCE_TIMEOUT_MS = 90_000L

        fun mlKitLanguage(language: TranscriptionLanguage): Int? = when (language) {
            TranscriptionLanguage.ENGLISH -> SummarizerOptions.Language.ENGLISH
            TranscriptionLanguage.JAPANESE -> SummarizerOptions.Language.JAPANESE
            TranscriptionLanguage.KOREAN -> SummarizerOptions.Language.KOREAN
            else -> null
        }

        /** Splits the model's bullet list into plain points. */
        fun parseBullets(raw: String?): List<String> =
            raw.orEmpty().lines()
                .map { it.trim().trimStart('-', '*', '•', '・', ' ').trim() }
                .filter { it.isNotEmpty() }
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Throwable) {
                cont.resumeWithException(e.cause ?: e)
            }
        }, Executor { it.run() })
        cont.invokeOnCancellation { cancel(true) }
    }
