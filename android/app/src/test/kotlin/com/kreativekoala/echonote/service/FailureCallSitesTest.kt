package com.kreativekoala.echonote.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.kreativekoala.echonote.diagnostics.FailureReporter
import com.kreativekoala.echonote.diagnostics.FakeTransport
import com.kreativekoala.echonote.diagnostics.ReportReason
import com.kreativekoala.echonote.diagnostics.TranscriptionFailure
import com.kreativekoala.echonote.diagnostics.ctx
import com.kreativekoala.echonote.diagnostics.testEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The app code that is supposed to report, driven far enough to see that it does. */
class FailureCallSitesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val transport = FakeTransport()

    @Before
    fun setUp() = FailureReporter.replaceEngineForTest(testEngine(transport))

    @After
    fun tearDown() {
        FailureReporter.replaceEngineForTest(null)
        unmockkAll()
    }

    // --- transcription ---

    @Test
    fun transcribingAMissingFileIsReportedWithoutItsName() = runTest {
        val service = TranscriptionService(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val result = service.transcribe("/nowhere/Secret meeting.m4a")
        assertTrue(result is TranscriptionResult.Error)
        val report = transport.reports.single()
        assertEquals("transcription.run", report.getString("flow"))
        assertEquals("file_missing", report.getString("message"))
        assertTrue(!transport.bodies.single().contains("Secret"))
    }

    @Test
    fun anEmptyTranscriptSaysWhy() {
        assertEquals(ReportReason.EMPTY_RESULT, TranscriptionFailure.noTextReason(null, hadAudio = true))
        assertEquals(ReportReason.NO_AUDIO, TranscriptionFailure.noTextReason(null, hadAudio = false))
        assertEquals(ReportReason.DECODE_FAILED, TranscriptionFailure.noTextReason(IOException(), hadAudio = false))
    }

    // --- model download and verification ---

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Answers one request with [status] and [body], then closes. */
    private fun <T> serving(status: String, body: ByteArray, block: (String) -> T): T = ServerSocket(0).use { server ->
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine().orEmpty().isNotEmpty()) Unit
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body)
                    flush()
                }
            }
        }
        block("http://127.0.0.1:${server.localPort}/model")
    }

    private fun downloadError(status: String, body: ByteArray, expected: ByteArray): Throwable = serving(status, body) { url ->
        val file = ModelFile(url, "model.onnx", expected.size.toLong(), sha(expected))
        runCatching { ModelDownloader.ensure(tmp.newFolder(), listOf(file)) {} }.exceptionOrNull()!!
    }

    @Test
    fun anHttpErrorIsADownloadFailureWithItsStatus() {
        val error = downloadError("404 Not Found", ByteArray(0), ByteArray(10))
        assertEquals("Download failed (HTTP 404)", error.message)
        ModelDownloader.reportFailure(error, SherpaModels.WHISPER_SMALL_DIR, AsrEngine.WHISPER_SMALL)
        val report = transport.reports.single()
        assertEquals("model.download", report.getString("flow"))
        assertEquals("http_status | ModelHttpException: Download failed (HTTP 404)", report.getString("message"))
        assertEquals("404", report.ctx("status"))
        assertEquals("whisper-small-int8", report.ctx("model"))
        assertEquals("WHISPER_SMALL", report.ctx("engine"))
    }

    @Test
    fun wrongBytesAreAVerificationFailure() {
        val error = downloadError("200 OK", ByteArray(10) { 1 }, ByteArray(10) { 2 })
        assertEquals("Downloaded model failed its integrity check", error.message)
        ModelDownloader.reportFailure(error, SherpaModels.DIARIZATION_DIR, AsrEngine.SENSE_VOICE)
        val report = transport.reports.single()
        assertEquals("model.verify", report.getString("flow"))
        assertTrue(report.getString("message").startsWith("checksum_mismatch | "))
    }

    @Test
    fun aShortFileIsToldApartFromACorruptOne() {
        val error = downloadError("200 OK", ByteArray(4), ByteArray(10))
        assertEquals(ReportReason.SIZE_SHORT, (error as ModelIntegrityException).reason)
        assertEquals(ReportReason.SIZE_LONG, ModelDownloader.integrityReason(11, 10))
        assertEquals(ReportReason.CHECKSUM_MISMATCH, ModelDownloader.integrityReason(10, 10))
    }

    // --- recording ---

    private fun recorder(microphoneGranted: Boolean = true): AudioRecorderService {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        mockkConstructor(MediaRecorder::class)
        stubRecorder()
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns
            if (microphoneGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        val context = mockk<Context>(relaxed = true)
        val dir: File = tmp.newFolder()
        every { context.filesDir } returns dir
        return AudioRecorderService(context)
    }

    private fun stubRecorder() {
        every { anyConstructed<MediaRecorder>().setOnErrorListener(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioSource(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setOutputFormat(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioEncoder(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioEncodingBitRate(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioSamplingRate(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioChannels(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setOutputFile(any<String>()) } returns Unit
        every { anyConstructed<MediaRecorder>().prepare() } returns Unit
        every { anyConstructed<MediaRecorder>().start() } returns Unit
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        every { anyConstructed<MediaRecorder>().release() } returns Unit
    }

    @Test
    fun aRecorderThatWillNotStartIsReportedWithoutTheFileName() {
        val service = recorder()
        every { anyConstructed<MediaRecorder>().prepare() } throws IOException("prepare failed.")
        assertNull(service.startRecording("My private title"))
        val report = transport.reports.single()
        assertEquals("recording.start", report.getString("flow"))
        assertEquals("exception | IOException: prepare failed.", report.getString("message"))
        assertEquals("prepare", report.ctx("stage"))
        assertTrue(!transport.bodies.single().contains("private title"))
    }

    @Test
    fun aStartThatFailsForLackOfPermissionIsNotReported() {
        val service = recorder(microphoneGranted = false)
        every { anyConstructed<MediaRecorder>().start() } throws RuntimeException("start failed.")
        assertNull(service.startRecording("x"))
        assertEquals(emptyList<String>(), transport.bodies)
    }

    @Test
    fun anErrorWhileWritingIsReported() {
        val service = recorder()
        val listener = slot<MediaRecorder.OnErrorListener>()
        every { anyConstructed<MediaRecorder>().setOnErrorListener(capture(listener)) } returns Unit
        service.startRecording("x")
        listener.captured.onError(null, 100, 0)
        val report = transport.reports.single()
        assertEquals("recording.write", report.getString("flow"))
        assertEquals("100", report.ctx("code"))
    }

    @Test
    fun anEmptyRecordingIsReportedAndStillReturnedAsBefore() {
        val service = recorder()
        service.startRecording("x")
        val result = service.stopRecording()
        assertEquals(0L, result!!.fileSize)
        val report = transport.reports.single()
        assertEquals("recording.stop", report.getString("flow"))
        assertEquals("empty_file", report.getString("message"))
    }

    @Test
    fun anImmediateStopThatThrowsIsNotReportedButALaterOneIs() {
        val service = recorder()
        every { anyConstructed<MediaRecorder>().stop() } throws RuntimeException("stop failed.")
        service.startRecording("x")
        assertNull(service.stopRecording())
        assertEquals(emptyList<String>(), transport.bodies)

        service.startRecording("x")
        Thread.sleep(1100)
        assertNull(service.stopRecording())
        assertEquals("exception | RuntimeException: stop failed.", transport.reports.single().getString("message"))
    }

    @Test
    fun cancellingARecordingReportsNothing() {
        val service = recorder()
        every { anyConstructed<MediaRecorder>().stop() } throws RuntimeException("stop failed.")
        service.startRecording("x")
        service.cancelRecording()
        assertEquals(emptyList<String>(), transport.bodies)
    }
}
