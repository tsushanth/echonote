package com.kreativekoala.echonote.service

import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.kreativekoala.echonote.data.model.TranscriptSegment
import java.io.File
import java.util.Locale

private const val SAMPLE_RATE = 16_000
private const val WINDOW_LIMIT = 28 * SAMPLE_RATE      // Whisper reads at most 30 s at a time
private const val CUT_SEARCH_FROM = 22 * SAMPLE_RATE   // cut in the quietest moment after this point
private const val MIN_WINDOW = SAMPLE_RATE / 3
private const val SILENCE_MEAN_ABS = 40                // of 32768; below this a window is treated as silence
private const val MAX_KEPT_SAMPLES = 30 * 60 * SAMPLE_RATE
private const val THREADS = 4

/** What one decoded window gave back; [timestamps] is empty when the engine returns no token times. */
private class Decoded(val tokens: Array<String>, val timestamps: FloatArray, val durations: FloatArray, val text: String)

/** One recognizer, offline or streaming, behind a single call. */
private interface WindowDecoder : AutoCloseable {
    fun decode(samples: FloatArray): Decoded
}

private class OfflineWindowDecoder(private val recognizer: OfflineRecognizer) : WindowDecoder {
    override fun decode(samples: FloatArray): Decoded {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            val r = recognizer.getResult(stream)
            return Decoded(r.tokens, r.timestamps, r.durations, r.text)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

/**
 * A streaming (online) transducer used window by window: the whole window is fed, followed by a second of silence so
 * the last words are flushed, then decoded to the end. Streaming models return real token times.
 */
private class OnlineWindowDecoder(private val recognizer: OnlineRecognizer) : WindowDecoder {
    override fun decode(samples: FloatArray): Decoded {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.acceptWaveform(FloatArray(SAMPLE_RATE), SAMPLE_RATE)
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val r = recognizer.getResult(stream)
            return Decoded(r.tokens, r.timestamps, FloatArray(0), r.text)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

/**
 * Builds the recognizer for [engine] from the files in [modelDir]; the file names are the ones [SherpaModels] saves
 * them under. Whisper and SenseVoice are given [languageCode]; the other engines find the language themselves.
 */
private fun createDecoder(engine: AsrEngine, modelDir: File, languageCode: String): WindowDecoder {
    fun path(name: String) = File(modelDir, name).absolutePath
    fun offline(model: OfflineModelConfig): WindowDecoder =
        OfflineWindowDecoder(OfflineRecognizer(null, OfflineRecognizerConfig(modelConfig = model)))
    fun transducer(modelType: String) = OfflineModelConfig(
        transducer = OfflineTransducerModelConfig(encoder = path("encoder.onnx"), decoder = path("decoder.onnx"), joiner = path("joiner.onnx")),
        tokens = path("tokens.txt"), numThreads = THREADS, modelType = modelType
    )
    return when (engine) {
        AsrEngine.WHISPER_SMALL -> offline(
            OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = path("encoder.onnx"), decoder = path("decoder.onnx"),
                    language = languageCode, task = "transcribe", enableTokenTimestamps = true
                ),
                tokens = path("tokens.txt"), numThreads = THREADS
            )
        )
        AsrEngine.SENSE_VOICE -> offline(
            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(model = path("model.onnx"), language = languageCode, useInverseTextNormalization = true),
                tokens = path("tokens.txt"), numThreads = THREADS
            )
        )
        AsrEngine.NEMO_FAST_CONFORMER, AsrEngine.NEMO_MULTILINGUAL -> offline(
            OfflineModelConfig(nemo = OfflineNemoEncDecCtcModelConfig(model = path("model.int8.onnx")), tokens = path("tokens.txt"), numThreads = THREADS)
        )
        AsrEngine.ZIPFORMER_RU, AsrEngine.ZIPFORMER_VI -> offline(transducer("transducer"))
        AsrEngine.DOLPHIN_BASE -> offline(
            OfflineModelConfig(dolphin = OfflineDolphinModelConfig(model = path("model.onnx")), tokens = path("tokens.txt"), numThreads = THREADS)
        )
        AsrEngine.OMNILINGUAL_300M -> offline(
            OfflineModelConfig(omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = path("model.onnx")), tokens = path("tokens.txt"), numThreads = THREADS)
        )
        AsrEngine.STARLING, AsrEngine.STREAMING_BN -> OnlineWindowDecoder(
            OnlineRecognizer(
                null,
                OnlineRecognizerConfig(
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(encoder = path("encoder.onnx"), decoder = path("decoder.onnx"), joiner = path("joiner.onnx")),
                        tokens = path("tokens.txt"), numThreads = THREADS
                    ),
                    enableEndpoint = false
                )
            )
        )
        AsrEngine.VOSK -> error("Vosk does not run through sherpa-onnx")
    }
}

/**
 * Receives 16 kHz mono audio in chunks, cuts it into windows at quiet moments, and runs the chosen
 * sherpa-onnx engine on each window. Keeps the audio too when [keepAudio] is set so speakers can be
 * identified afterwards (dropped past 30 minutes, which sets [audioTooLong]).
 *
 * Whisper's exports return no token times, so its words are timed by spreading them over the speech in
 * each window; every other engine returns real times, which are used as they are.
 */
class SherpaTranscriber(
    private val engine: AsrEngine,
    private val modelDir: File,
    private val languageCode: String,
    private val keepAudio: Boolean,
    private val onPartial: (String) -> Unit
) : AutoCloseable {

    private val decoder: WindowDecoder = createDecoder(engine, modelDir, languageCode)

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
        val result = decoder.decode(floats)
        var found = tokensToWords(result.tokens, result.timestamps, result.durations, windowStartMs)
            .let { clampStartsToSpeech(it, speechSpans(window, count), windowStartMs) }
        if (found.isEmpty()) {
            // No token times (Whisper): time the words over the speech in this window.
            val spoken = groupTokens(result.tokens).map { it.text }
                .ifEmpty { result.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
            found = distributeOverSpeech(spoken, speechSpans(window, count), windowStartMs)
        }
        // Some engines write in capitals (the Vietnamese Zipformer); show lower case like the other engines.
        if (engine.upperCaseOutput) found = found.map { it.copy(word = it.word.lowercase(Locale.ROOT)) }
        words += found
        if (found.isNotEmpty()) onPartial(joinWords(words.map { it.word }))
    }

    override fun close() {
        decoder.close()
    }
}
