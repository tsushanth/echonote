package com.kreativekoala.echonote.service

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A source of 16 kHz mono 16-bit little-endian PCM. All methods are called from one thread. */
interface LiveAudioSource {
    fun start()
    /** Blocks until some audio is read. Returns bytes read; a negative value means the source failed. */
    fun read(buf: ByteArray): Int
    fun stop()
    fun release()
}

/** Result of feeding audio to a recognizer: the best text so far and whether the utterance is finished. */
data class LiveHypothesis(val text: String, val isFinal: Boolean)

interface LiveRecognizer {
    fun accept(buf: ByteArray, len: Int): LiveHypothesis
    fun close()
}

/**
 * Runs live recognition on two background threads, completely apart from the recorder:
 *  - capture thread: reads PCM and hands 100 ms chunks to a bounded queue; it never waits for recognition.
 *  - worker thread: pulls chunks and feeds the recognizer.
 * Drop policy: if recognition falls behind and the queue (about 2.4 s of audio) is full, the NEW chunk is
 * discarded. Live text may miss words; memory stays bounded and the microphone buffer never overflows.
 * Any Throwable on either thread ends only this session ([onFailed]); it is never rethrown.
 * The worker thread owns and closes the recognizer, the capture thread owns the source, so nothing is
 * closed while it is in use. [onClosed] runs once, after both are released.
 */
class LiveTranscriptSession(
    private val source: LiveAudioSource,
    private val recognizer: LiveRecognizer,
    private val accumulator: LiveTranscriptAccumulator,
    private val onText: (String) -> Unit,
    private val onFailed: (Throwable) -> Unit,
    private val onClosed: () -> Unit,
    queueCapacity: Int = 24,
    private val chunkBytes: Int = 3200
) {
    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacity)
    private val running = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val remaining = AtomicInteger(2)
    @Volatile var droppedChunks = 0
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        spawn("live-capture", Thread.NORM_PRIORITY, ::captureLoop)
        spawn("live-recognize", Thread.MIN_PRIORITY + 1, ::recognizeLoop)
    }

    fun setPaused(value: Boolean) { paused.set(value) }

    /** Asks both threads to finish; returns immediately. */
    fun stop() { running.set(false) }

    private fun spawn(name: String, priority: Int, body: () -> Unit) {
        val t = Thread({ body() }, name)
        t.priority = priority
        t.isDaemon = true
        // Last line of defence: nothing thrown here may reach the app's crash handler.
        t.setUncaughtExceptionHandler { _, e -> fail(e) }
        t.start()
    }

    private fun fail(e: Throwable) {
        running.set(false)
        if (failed.compareAndSet(false, true)) {
            try { onFailed(e) } catch (_: Throwable) {}
        }
    }

    private fun finishOne() {
        if (remaining.decrementAndGet() == 0) {
            try { onClosed() } catch (_: Throwable) {}
        }
    }

    private fun captureLoop() {
        var started = false
        try {
            val buf = ByteArray(chunkBytes)
            while (running.get()) {
                if (paused.get()) {
                    if (started) { source.stop(); started = false }
                    Thread.sleep(100)
                    continue
                }
                if (!started) { source.start(); started = true }
                val n = source.read(buf)
                if (n < 0) throw IllegalStateException("audio source read failed: $n")
                if (n == 0) continue
                if (!queue.offer(buf.copyOf(n))) droppedChunks++
            }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            try { if (started) source.stop() } catch (_: Throwable) {}
            try { source.release() } catch (_: Throwable) {}
            finishOne()
        }
    }

    private fun recognizeLoop() {
        try {
            var last = ""
            while (running.get()) {
                val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                val h = recognizer.accept(chunk, chunk.size)
                if (h.isFinal) accumulator.onFinal(h.text) else accumulator.onPartial(h.text)
                val shown = accumulator.text()
                if (shown != last) { last = shown; onText(shown) }
            }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            queue.clear()
            try { recognizer.close() } catch (_: Throwable) {}
            finishOne()
        }
    }
}
