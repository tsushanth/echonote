package com.kreativekoala.echonote.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LiveTranscriptAccumulatorTest {
    @Test
    fun showsFinishedUtterancesThenThePartial() {
        val a = LiveTranscriptAccumulator()
        a.onPartial("hello")
        assertEquals("hello", a.text())
        a.onFinal("hello world")
        assertEquals("hello world", a.text())
        a.onPartial("how are")
        assertEquals("hello world how are", a.text())
        a.onFinal("how are you")
        assertEquals("hello world how are you", a.text())
    }

    @Test
    fun aNewPartialReplacesTheOldOne() {
        val a = LiveTranscriptAccumulator()
        a.onPartial("the cat")
        a.onPartial("the cat sat")
        assertEquals("the cat sat", a.text())
    }

    @Test
    fun blankFinalDropsThePartialWithoutAddingText() {
        val a = LiveTranscriptAccumulator()
        a.onFinal("one")
        a.onPartial("tw")
        a.onFinal("")
        assertEquals("one", a.text())
    }

    @Test
    fun longTextShowsOnlyTheEndAndStaysWithinTheCap() {
        val a = LiveTranscriptAccumulator(maxChars = 100)
        repeat(200) { a.onFinal("word$it more words here") }
        a.onPartial("latest partial")
        val t = a.text()
        assertTrue(t.length <= 101)
        assertTrue(t.startsWith("…"))
        assertTrue(t.endsWith("latest partial"))
        assertTrue("did not start mid-word", t.drop(1).startsWith("word") || t.drop(1).startsWith("more") ||
            t.drop(1).startsWith("words") || t.drop(1).startsWith("here"))
    }

    @Test
    fun memoryStaysBoundedForVeryLongRecordings() {
        val a = LiveTranscriptAccumulator(maxChars = 50)
        repeat(10_000) { a.onFinal("sentence number $it") }
        assertTrue(a.text().length <= 51)
        assertTrue(a.text().endsWith("sentence number 9999"))
    }

    @Test
    fun chineseGetsNoSpaces() {
        val a = LiveTranscriptAccumulator()
        a.onFinal("你 好 世 界")
        a.onPartial("今 天")
        assertEquals("你好世界今天", a.text())
    }

    @Test
    fun japaneseMixedWithLatinKeepsSpacesOnlyBetweenLatinWords() {
        assertEquals("私は iPhone を買った", normalizeSpacing("私 は iPhone を 買 っ た"))
        assertEquals("hello world", normalizeSpacing("  hello   world "))
    }

    @Test
    fun truncationNeverSplitsASurrogatePair() {
        val a = LiveTranscriptAccumulator(maxChars = 10)
        a.onFinal("😀".repeat(20))
        val t = a.text()
        assertFalse(t.drop(1).first().isLowSurrogate())
    }

    @Test
    fun parsesVoskJson() {
        assertEquals("hi there", parseVoskText("""{"partial":"hi there"}""", "partial"))
        assertEquals("done", parseVoskText("""{"text":" done "}""", "text"))
        assertEquals("", parseVoskText("not json", "text"))
        assertEquals("", parseVoskText("""{"other":1}""", "text"))
    }
}

class LiveTranscriptSessionTest {
    private class FakeSource(private val failOnRead: Boolean = false, private val perReadDelayMs: Long = 1) : LiveAudioSource {
        val released = CountDownLatch(1)
        val started = AtomicInteger()
        override fun start() { started.incrementAndGet() }
        override fun read(buf: ByteArray): Int {
            Thread.sleep(perReadDelayMs)
            return if (failOnRead) -1 else buf.size
        }
        override fun stop() {}
        override fun release() { released.countDown() }
    }

    private class FakeRecognizer(val onAccept: (Int) -> LiveHypothesis) : LiveRecognizer {
        val closed = CountDownLatch(1)
        val calls = AtomicInteger()
        override fun accept(buf: ByteArray, len: Int) = onAccept(calls.incrementAndGet())
        override fun close() { closed.countDown() }
    }

    private fun session(
        source: LiveAudioSource, rec: LiveRecognizer, texts: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf()),
        failed: CountDownLatch = CountDownLatch(1), closed: CountDownLatch = CountDownLatch(1), cap: Int = 24
    ) = LiveTranscriptSession(source, rec, LiveTranscriptAccumulator(), { texts.add(it) }, { failed.countDown() }, { closed.countDown() }, cap)

    @Test
    fun aRecognizerThatThrowsEndsOnlyTheLiveSessionAndReleasesEverything() {
        val src = FakeSource()
        val rec = FakeRecognizer { throw OutOfMemoryError("boom") }
        val failed = CountDownLatch(1); val closed = CountDownLatch(1)
        session(src, rec, failed = failed, closed = closed).start()
        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(src.released.await(1, TimeUnit.SECONDS))
        assertTrue(rec.closed.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun aMicrophoneThatFailsEndsOnlyTheLiveSession() {
        val src = FakeSource(failOnRead = true)
        val rec = FakeRecognizer { LiveHypothesis("", false) }
        val failed = CountDownLatch(1); val closed = CountDownLatch(1)
        session(src, rec, failed = failed, closed = closed).start()
        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertTrue(closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun textFlowsFromRecognizerToCallback() {
        val texts = java.util.Collections.synchronizedList(mutableListOf<String>())
        val rec = FakeRecognizer { n -> if (n < 3) LiveHypothesis("hel", false) else LiveHypothesis("hello", true) }
        val closed = CountDownLatch(1)
        val s = session(FakeSource(), rec, texts, closed = closed)
        s.start()
        val deadline = System.currentTimeMillis() + 5000
        while (!texts.contains("hello") && System.currentTimeMillis() < deadline) Thread.sleep(10)
        s.stop()
        assertTrue(texts.contains("hello"))
        assertTrue(closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun aSlowRecognizerDropsAudioInsteadOfQueueingForever() {
        val gate = CountDownLatch(1)
        val rec = FakeRecognizer { gate.await(5, TimeUnit.SECONDS); LiveHypothesis("", false) }
        val s = session(FakeSource(perReadDelayMs = 0), rec, cap = 4)
        s.start()
        val deadline = System.currentTimeMillis() + 5000
        while (s.droppedChunks < 50 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("expected dropped chunks, got ${s.droppedChunks}", s.droppedChunks >= 50)
        s.stop(); gate.countDown()
    }

    @Test
    fun pausingReleasesTheMicAndResumingReopensIt() {
        val src = FakeSource()
        val s = session(src, FakeRecognizer { LiveHypothesis("", false) })
        s.start()
        Thread.sleep(100)
        s.setPaused(true); Thread.sleep(300)
        s.setPaused(false); Thread.sleep(300)
        s.stop()
        assertEquals(2, src.started.get())
    }
}
