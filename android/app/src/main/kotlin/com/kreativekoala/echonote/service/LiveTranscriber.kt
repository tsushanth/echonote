package com.kreativekoala.echonote.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveStatus { OFF, STARTING, LISTENING, NO_MODEL, UNAVAILABLE }

data class LiveTranscriptState(val status: LiveStatus = LiveStatus.OFF, val text: String = "")

/**
 * Live words while recording, using the Vosk model that Fast transcription already downloaded.
 *
 * Safety contract: this class never touches the recorder and no public method throws. It opens its own
 * 16 kHz AudioRecord only after the recorder is running, only on Android 10+ (where apps may capture
 * concurrently), and never downloads anything. If anything fails it ends the live feature for that
 * recording and the recording carries on.
 */
@Singleton
class LiveTranscriber @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(LiveTranscriptState())
    val state: StateFlow<LiveTranscriptState> = _state

    private val lock = Any()
    private var generation = 0
    private var session: LiveTranscriptSession? = null

    /** True when the language's Vosk model is already on disk (same check as Fast transcription). */
    fun isModelOnDisk(language: TranscriptionLanguage): Boolean = try {
        File(context.filesDir, language.modelDirName).let { it.exists() && it.listFiles()?.isNotEmpty() == true }
    } catch (_: Throwable) { false }

    fun start(language: TranscriptionLanguage) {
        try {
            val gen: Int
            synchronized(lock) {
                stopLocked()
                gen = ++generation
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                _state.value = LiveTranscriptState(LiveStatus.UNAVAILABLE); return
            }
            if (!isModelOnDisk(language)) {
                _state.value = LiveTranscriptState(LiveStatus.NO_MODEL); return
            }
            _state.value = LiveTranscriptState(LiveStatus.STARTING)
            val loader = Thread({ load(language, gen) }, "live-load")
            loader.priority = Thread.MIN_PRIORITY + 1
            loader.isDaemon = true
            loader.setUncaughtExceptionHandler { _, _ -> failIfCurrent(gen) }
            loader.start()
        } catch (_: Throwable) {
            _state.value = LiveTranscriptState(LiveStatus.UNAVAILABLE)
        }
    }

    fun setPaused(paused: Boolean) {
        try { synchronized(lock) { session?.setPaused(paused) } } catch (_: Throwable) {}
    }

    fun stop() {
        try {
            synchronized(lock) { generation++; stopLocked() }
            _state.value = LiveTranscriptState()
        } catch (_: Throwable) {}
    }

    private fun stopLocked() {
        session?.stop()
        session = null
    }

    private fun failIfCurrent(gen: Int) {
        synchronized(lock) {
            if (gen != generation) return
            stopLocked()
        }
        _state.value = LiveTranscriptState(LiveStatus.UNAVAILABLE)
    }

    private fun load(language: TranscriptionLanguage, gen: Int) {
        var model: Model? = null
        try {
            model = Model(File(context.filesDir, language.modelDirName).absolutePath)
            val recognizer = VoskLiveRecognizer(Recognizer(model, SAMPLE_RATE.toFloat()))
            val source = AudioRecordSource()
            val ownedModel = model
            val s = LiveTranscriptSession(
                source = source,
                recognizer = recognizer,
                accumulator = LiveTranscriptAccumulator(),
                onText = { text ->
                    synchronized(lock) {
                        if (gen == generation) _state.value = LiveTranscriptState(LiveStatus.LISTENING, text)
                    }
                },
                onFailed = { failIfCurrent(gen) },
                onClosed = { try { ownedModel.close() } catch (_: Throwable) {} }
            )
            synchronized(lock) {
                if (gen != generation) {
                    // Recording ended while the model loaded: never open the microphone.
                    recognizer.close(); model.close(); return
                }
                session = s
                model = null // ownership moved to the session
                s.start()    // only spawns threads; inside the lock so stop() cannot slip in before it
                _state.value = LiveTranscriptState(LiveStatus.LISTENING)
            }
        } catch (_: Throwable) {   // includes OutOfMemoryError from loading the model
            try { model?.close() } catch (_: Throwable) {}
            failIfCurrent(gen)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}

private class VoskLiveRecognizer(private val r: Recognizer) : LiveRecognizer {
    override fun accept(buf: ByteArray, len: Int): LiveHypothesis =
        if (r.acceptWaveForm(buf, len)) LiveHypothesis(parseVoskText(r.result, "text"), true)
        else LiveHypothesis(parseVoskText(r.partialResult, "partial"), false)

    override fun close() = r.close()
}

/** A second, independent capture of the microphone. Never shares or alters the recorder's stream. */
private class AudioRecordSource : LiveAudioSource {
    private var rec: AudioRecord? = null

    override fun start() {
        val min = AudioRecord.getMinBufferSize(
            LiveTranscriber.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(min > 0) { "no buffer size" }
        val size = maxOf(min * 4, LiveTranscriber.SAMPLE_RATE * 2) // at least 1 s of 16-bit audio
        val r = rec ?: AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, LiveTranscriber.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size
        ).also { rec = it }
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }
        r.startRecording()
        check(r.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "microphone busy" }
    }

    override fun read(buf: ByteArray): Int = rec?.read(buf, 0, buf.size) ?: -1
    override fun stop() { rec?.stop() }
    override fun release() { rec?.release(); rec = null }
}
