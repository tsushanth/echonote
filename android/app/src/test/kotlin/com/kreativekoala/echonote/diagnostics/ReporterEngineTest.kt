package com.kreativekoala.echonote.diagnostics

import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReporterEngineTest {
    private var original: Thread.UncaughtExceptionHandler? = null
    private val reachedPrevious = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> reachedPrevious.add(t) }
    }

    @After
    fun tearDown() = Thread.setDefaultUncaughtExceptionHandler(original)

    private fun crashNow(t: Throwable) =
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), t)

    @Test
    fun aHandledFailureIsSent() {
        val transport = FakeTransport()
        testEngine(transport).failure(ReportFlow.RECORDING_START, IOException("prepare failed."), ReportReason.EXCEPTION, mapOf("stage" to "prepare"))
        val report = transport.reports.single()
        assertEquals("failure", report.getString("kind"))
        assertEquals("recording.start", report.getString("flow"))
        assertEquals("exception | IOException: prepare failed.", report.getString("message"))
        assertEquals("prepare", report.ctx("stage"))
    }

    @Test
    fun cancellationsAndOfflineErrorsAreNotFailures() {
        val transport = FakeTransport()
        val engine = testEngine(transport)
        listOf(
            CancellationException("left the screen"),
            IOException("wrapped", CancellationException()),
            UnknownHostException("huggingface.co"),
            ConnectException("refused"),
            SocketTimeoutException("timeout"),
            SSLException("Connection reset by peer"),
            ProtocolException("unexpected end of stream"),
            IOException("wrapped", UnknownHostException())
        ).forEach { engine.failure(ReportFlow.MODEL_DOWNLOAD, it, ReportReason.EXCEPTION) }
        assertEquals(emptyList<String>(), transport.bodies)
    }

    @Test
    fun aBadCertificateAndAFullDiskAreStillReported() {
        val transport = FakeTransport()
        val engine = testEngine(transport)
        engine.failure(ReportFlow.MODEL_DOWNLOAD, SSLHandshakeException("bad").apply { initCause(CertificateException()) })
        engine.failure(ReportFlow.MODEL_DOWNLOAD, IOException("write failed: ENOSPC (No space left on device)"))
        assertEquals(2, transport.bodies.size)
        assertTrue(transport.reports[1].getString("message").startsWith("storage_full"))
    }

    @Test
    fun aRepeatedFailureIsSentOnce() {
        val transport = FakeTransport()
        val engine = testEngine(transport)
        repeat(50) { engine.failure(ReportFlow.TRANSCRIPTION_RUN, reason = ReportReason.EMPTY_RESULT) }
        assertEquals(1, transport.bodies.size)
    }

    @Test
    fun aBrokenTransportNeverReachesTheCaller() {
        val engine = testEngine(FakeTransport(error = IllegalStateException("boom")))
        engine.failure(ReportFlow.TRANSCRIPTION_RUN, reason = ReportReason.EMPTY_RESULT)
        engine.start()
        crashNow(RuntimeException("x"))
        assertEquals(1, reachedPrevious.size)
    }

    @Test
    fun aCrashIsSavedBeforeItIsSentThenForgottenAndPassedOn() {
        val store = MapStore()
        var savedWhenSent: String? = null
        val transport = object : ReportTransport {
            val bodies = mutableListOf<String>()
            override fun post(body: String, timeoutMs: Int): Boolean {
                savedWhenSent = store.read(ReporterEngine.KEY_PENDING_CRASH)
                bodies.add(body)
                return true
            }
        }
        ReporterEngine("1.15.1", TEST_DEVICE, store, transport, { it.run() }).start()
        val boom = IllegalStateException("private words")
        crashNow(boom)
        assertEquals(transport.bodies.single(), savedWhenSent)
        assertTrue(transport.bodies.single().contains("\"kind\":\"crash\""))
        assertNull(store.read(ReporterEngine.KEY_PENDING_CRASH))
        assertEquals(listOf<Throwable>(boom), reachedPrevious)
    }

    @Test
    fun anUnsentCrashIsKeptAndSentOnTheNextStart() {
        val store = MapStore()
        testEngine(FakeTransport(accept = false), store).start()
        crashNow(RuntimeException("x"))
        val pending = store.read(ReporterEngine.KEY_PENDING_CRASH)
        assertNotNull(pending)

        val second = FakeTransport()
        testEngine(second, store).start()
        assertEquals(listOf(pending), second.bodies)
        assertNull(store.read(ReporterEngine.KEY_PENDING_CRASH))
    }

    @Test
    fun aCrashLoopSendsOneReport() {
        val store = MapStore()
        val transport = FakeTransport()
        repeat(5) {
            Thread.setDefaultUncaughtExceptionHandler { _, t -> reachedPrevious.add(t) }
            testEngine(transport, store).start() // a new process each time
            crashNow(sameCrash())
        }
        assertEquals(1, transport.bodies.size)
        assertEquals(5, reachedPrevious.size)
    }

    private fun sameCrash() = IllegalStateException("x")
}
