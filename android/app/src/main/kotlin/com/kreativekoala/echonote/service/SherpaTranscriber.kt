package com.kreativekoala.echonote.service

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.kreativekoala.echonote.data.model.TranscriptSegment
import java.io.File

private const val SAMPLE_RATE = 16_000
private const val WINDOW_LIMIT = 28 * SAMPLE_RATE      // Whisper reads at most 30 s at a time
private const val CUT_SEARCH_FROM = 22 * SAMPLE_RATE   // cut in the quietest moment after this point
private const val MIN_WINDOW = SAMPLE_RATE / 3
private const val SILENCE_MEAN_ABS = 40                // of 32768; below this a window is treated as silence
private const val MAX_KEPT_SAMPLES = 30 * 60 * SAMPLE_RATE

/**
 * Receives 16 kHz mono audio in chunks, cuts it into windows at quiet moments, and runs the chosen
 * sherpa-onnx engine on each window. Keeps the audio too when [keepAudio] is set so speakers can be
 * identified afterwards (dropped past 30 minutes, which sets [audioTooLong]).
 *
 * Whisper's exports return no token times, so its words are timed by spreading them over the speech in
 * each window; SenseVoice and NeMo return real times, which are used as they are.
 */
class SherpaTranscriber(
    private val engine: AsrEngine,
    private val modelDir: File,
    private val languageCode: String,
    private val keepAudio: Boolean,
    private val onPartial: (String) -> Unit
) : AutoCloseable {

    private val recognizer: OfflineRecognizer = OfflineRecognizer(
        null,
        OfflineRecognizerConfig(
            modelConfig = when (engine) {
                AsrEngine.WHISPER_SMALL -> OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(modelDir, "encoder.onnx").absolutePath,
                        decoder = File(modelDir, "decoder.onnx").absolutePath,
                        language = languageCode,
                        task = "transcribe",
                        enableTokenTimestamps = true
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 4
                )
                AsrEngine.SENSE_VOICE -> OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = File(modelDir, "model.onnx").absolutePath,
                        language = languageCode,
                        useInverseTextNormalization = true
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 4
                )
                AsrEngine.NEMO_FAST_CONFORMER -> OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = File(modelDir, "model.int8.onnx").absolutePath),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 4
                )
                AsrEngine.VOSK -> error("Vosk does not run through sherpa-onnx")
            }
        )
    )

    private val window = ShortArray(WINDOW_LIMIT + SAMPLE_RATE)
    private var windowLen = 0
    private var windowStartMs = 0L

    val words = mutableListOf<TranscriptSegment>()
    private var kept = ShortArray(if (keepAudio) SAMPLE_RATE * 60 else 0)
    private var keptLen = 0
    var audioTooLong = false
        private set

    /** Audio kept for diarization, or null when not requested or too long. */
    fun keptAudio(): Pair<ShortArray, Int>? =
        if (keepAudio && !audioTooLong) kept to keptLen else null

    fun accept(samples: ShortArray, count: Int) {
        if (keepAudio && !audioTooLong) keep(samples, count)
        var offset = 0
        while (offset < count) {
            val take = minOf(count - offset, window.size - windowLen)
            System.arraycopy(samples, offset, window, windowLen, take)
            windowLen += take
            offset += take
            while (windowLen >= WINDOW_LIMIT) {
                val cut = quietestFrameStart(window, CUT_SEARCH_FROM, WINDOW_LIMIT)
                    .coerceIn(MIN_WINDOW, windowLen)
                decodeWindow(cut)
                System.arraycopy(window, cut, window, 0, windowLen - cut)
                windowLen -= cut
                windowStartMs += cut * 1000L / SAMPLE_RATE
            }
        }
    }

    fun finish() {
        if (windowLen > 0) decodeWindow(windowLen)
        windowLen = 0
    }

    private fun keep(samples: ShortArray, count: Int) {
        if (keptLen + count > MAX_KEPT_SAMPLES) {
            audioTooLong = true
            kept = ShortArray(0)
            return
        }
        if (keptLen + count > kept.size) kept = kept.copyOf(maxOf(kept.size * 2, keptLen + count))
        System.arraycopy(samples, 0, kept, keptLen, count)
        keptLen += count
    }

    private fun decodeWindow(count: Int) {
        if (count < MIN_WINDOW) return
        var sum = 0L
        for (i in 0 until count) sum += kotlin.math.abs(window[i].toInt())
        if (sum / count < SILENCE_MEAN_ABS) return // Whisper invents text on silence

        val floats = FloatArray(count) { window[it] / 32768f }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(floats, SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            var found = tokensToWords(result.tokens, result.timestamps, result.durations, windowStartMs)
            if (found.isEmpty()) {
                // No token times (Whisper): time the words over the speech in this window.
                val spoken = groupTokens(result.tokens).map { it.text }
                    .ifEmpty { result.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
                found = distributeOverSpeech(spoken, speechSpans(window, count), windowStartMs)
            }
            words += found
            if (found.isNotEmpty()) onPartial(joinWords(words.map { it.word }))
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }
}
