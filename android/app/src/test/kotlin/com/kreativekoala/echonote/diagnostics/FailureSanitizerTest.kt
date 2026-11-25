package com.kreativekoala.echonote.diagnostics

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.AsrEngine
import com.kreativekoala.echonote.service.SherpaModels
import java.io.FileNotFoundException
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureSanitizerTest {

    /** Things that must never leave the phone, each with the fragments that would give it away. */
    private val secrets: Map<String, List<String>> = mapOf(
        "so the zanzibar merger closes on quillfeather day" to listOf("zanzibar", "quillfeather", "merger"),
        "Interview with Dr Marlowe Vantablack" to listOf("Interview", "Marlowe", "Vantablack"),
        "Xyzzy" to listOf("Xyzzy"),
        "/data/user/0/com.kreativekoala.echonote/files/Recordings/Recording_2026-01-02_10-11-12.m4a" to
            listOf("Recordings", "Recording_", "m4a", "echonote\\/files", "data\\/user"),
        "/storage/emulated/0/Download/Zoë Quillfeather, private notes" to listOf("emulated", "Quillfeather", "private"),
        "therapy-session.wav" to listOf("therapy", "wav"),
        "content://media/external/audio/media/84213" to listOf("content:", "84213", "external"),
        "divorce lawyer near me" to listOf("divorce", "lawyer"),
        "30.267153,-97.743061" to listOf("30.267", "97.743"),
        "1200 Congress Avenue, Austin" to listOf("Congress", "Austin"),
        "jane.roe@example.com" to listOf("jane", "example.com"),
        "9f1c2d3e-4b5a-4c6d-8e9f-0a1b2c3d4e5f" to listOf("9f1c2d3e", "0a1b2c3d4e5f"),
        "прием у врача" to listOf("прием", "врача", "\\u043f")
    )

    private fun throwablesAround(secret: String): List<Throwable> = listOf(
        RuntimeException(secret),
        RuntimeException("stop failed. $secret"),
        IOException("$secret: open failed: ENOENT (No such file or directory)"),
        FileNotFoundException(secret),
        IllegalStateException("Could not save $secret"),
        IllegalStateException("Archive is missing $secret"),
        IllegalStateException("Extracted $secret failed its integrity check"),
        IllegalArgumentException("Cannot open \"$secret\""),
        JSONException("Unterminated object at character 15 of {\"text\": \"$secret"),
        NumberFormatException("For input string: \"$secret\""),
        OutOfMemoryError("Failed to allocate a 1024 byte allocation for $secret"),
        RuntimeException("outer $secret", IOException("inner $secret", IllegalStateException(secret)))
    )

    private fun payload(
        t: Throwable?,
        reason: ReportReason? = ReportReason.EXCEPTION,
        context: Map<String, String> = emptyMap()
    ) = ReportPayload.build("failure", "1.15.1", ReportFlow.TRANSCRIPTION_RUN, t, reason, context, TEST_DEVICE)

    private fun assertClean(body: String, fragments: List<String>, what: String) {
        for (fragment in fragments) {
            assertFalse("$fragment leaked from $what: $body", body.contains(fragment, ignoreCase = true))
        }
    }

    @Test
    fun noSecretInAnExceptionMessageOrItsCausesIsSent() {
        for ((secret, fragments) in secrets) {
            for (t in throwablesAround(secret)) {
                for (kind in listOf("failure", "crash")) {
                    val body = ReportPayload.build(kind, "1.15.1", ReportFlow.CRASH, t, null, emptyMap(), TEST_DEVICE)
                    assertClean(body, fragments, "$secret in ${t.javaClass.simpleName}")
                }
            }
        }
    }

    @Test
    fun noSecretPassedAsContextIsSent() {
        // Flows and reasons are enums, so context is the only place a caller could put a string.
        val keys = listOf(
            "engine", "model", "stage", "status", "code", "format", "duration", "thread", "free_mb",
            "title", "file", "path", "query", "text", "transcript", "location", "user", "install_id"
        )
        for ((secret, fragments) in secrets) {
            val body = payload(null, context = keys.associateWith { secret })
            assertClean(body, fragments, secret)
            assertEquals(emptyMap<String, String>(), FailureSanitizer.context(keys.associateWith { secret }))
        }
    }

    @Test
    fun anUnknownContextKeyIsDroppedAndAKnownOneKept() {
        val context = JSONObject(payload(null, context = mapOf("title" to "load", "stage" to "load"))).getJSONObject("context")
        assertFalse(context.has("title"))
        assertEquals("load", context.getString("stage"))
    }

    @Test
    fun everyModelAndEngineNameTheAppUsesSurvivesTheContextFilter() {
        val models = TranscriptionLanguage.entries.map { it.modelDirName } +
            AsrEngine.entries.mapNotNull { SherpaModels.forEngine(it)?.dirName } + SherpaModels.DIARIZATION_DIR
        for (model in models) assertEquals(model, FailureSanitizer.context(mapOf("model" to model))["model"])
        for (engine in AsrEngine.entries) {
            assertEquals(engine.name, FailureSanitizer.context(mapOf("engine" to engine.name))["engine"])
        }
    }

    @Test
    fun theReportCarriesTheFieldsTheServerExpects() {
        val json = JSONObject(payload(IOException("write failed: ENOSPC (No space left on device)"), reason = ReportReason.DECODE_FAILED))
        assertEquals("failure", json.getString("kind"))
        assertEquals("android", json.getString("platform"))
        assertEquals("1.15.1", json.getString("version"))
        assertEquals("transcription.run", json.getString("flow"))
        assertEquals("storage_full | decode_failed | IOException: ENOSPC", json.getString("message"))
        assertEquals("Google Pixel 9 Pro", json.getJSONObject("context").getString("device"))
        assertEquals("36", json.getJSONObject("context").getString("sdk"))
    }

    @Test
    fun knownTechnicalMessagesAreKept() {
        for (message in listOf(
            "stop failed.", "start failed: -38", "prepare failed.", "Download failed (HTTP 404)",
            "Downloaded model failed its integrity check", "microphone busy", "audio source read failed: -3",
            "Failed to create a model", "Error 0xfffffff4"
        )) assertEquals(message, FailureSanitizer.safeDetail(message))
        assertEquals(
            "Extracted file failed its integrity check",
            FailureSanitizer.safeDetail("Extracted model.int8.onnx failed its integrity check")
        )
        assertEquals("", FailureSanitizer.safeDetail("something nobody has listed"))
        assertEquals("", FailureSanitizer.safeDetail(null))
    }

    @Test
    fun theStackHasClassNamesAndCodeLocationsForEveryCauseAndNoMessages() {
        val stack = FailureSanitizer.stack(RuntimeException("secretone", IOException("secrettwo")))
        assertTrue(stack.startsWith("java.lang.RuntimeException\n"))
        assertTrue(stack.contains("Caused by: java.io.IOException\n"))
        assertTrue(stack.contains("FailureSanitizerTest.kt:"))
        assertFalse(stack.contains("secret"))
        assertTrue(stack.length <= FailureSanitizer.MAX_STACK)
    }

    @Test
    fun theLargestReportStaysUnderTheServerLimit() {
        var t: Throwable = IllegalStateException("x")
        repeat(10) { t = IllegalStateException("x", t) }
        t.stackTrace = Array(500) { StackTraceElement("com.example." + "A".repeat(200), "method", "A.kt", it) }
        assertTrue(payload(t).toByteArray().size < 16 * 1024)
    }
}
