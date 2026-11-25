package com.kreativekoala.echonote.diagnostics

import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.AsrEngine
import com.kreativekoala.echonote.service.SherpaModels
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CancellationException

/*
 * The pure parts of failure reporting (no Android classes, so they run in plain JVM unit tests): when the reporter
 * may start, what is allowed to leave the device, how it is shaped, and how often it may be sent.
 * [FailureReporter] is the thin Android shell around this file.
 */

/** Stable flow names. Reports are counted by [code], so do not change one casually. */
enum class ReportFlow(val code: String) {
    CRASH("uncaught"),
    TRANSCRIPTION_RUN("transcription.run"),
    TRANSCRIPTION_SPEAKERS("transcription.speakers"),
    TRANSCRIPTION_UNCERTAIN_WORDS("transcription.uncertain_words"),
    TRANSCRIPTION_LIVE("transcription.live"),
    MODEL_DOWNLOAD("model.download"),
    MODEL_VERIFY("model.verify"),
    MODEL_LOAD("model.load"),
    RECORDING_START("recording.start"),
    RECORDING_WRITE("recording.write"),
    RECORDING_PAUSE("recording.pause"),
    RECORDING_STOP("recording.stop"),
    BILLING_ENTITLEMENT("billing.entitlement")
}

/** Why a flow failed. An enum so that no free text can be passed as a reason. */
enum class ReportReason {
    EXCEPTION, OUT_OF_MEMORY,
    FILE_MISSING, DECODE_FAILED, NO_AUDIO, EMPTY_RESULT,
    HTTP_STATUS, SIZE_SHORT, SIZE_LONG, CHECKSUM_MISMATCH, ENTRY_SIZE, MISSING_ENTRY, CACHED_MODEL_UNREADABLE,
    MEDIA_RECORDER_ERROR, NO_OUTPUT_FILE, EMPTY_FILE,
    CONNECT_TIMEOUT, SETUP_FAILED, QUERY_TIMEOUT, QUERY_FAILED;

    val code: String get() = name.lowercase(Locale.ROOT)
}

/** Whether the reporter may start at all. When this is false there is no crash handler, no storage and no network. */
object ReportGate {
    fun allows(ingestKey: String, debugBuild: Boolean, testEnvironment: () -> Boolean): Boolean =
        ingestKey.isNotBlank() && !debugBuild && !testEnvironment()
}

/**
 * Privacy contract, enforced here and covered by FailureSanitizerTest: no audio, transcript or search text, recording
 * titles, file names or paths, URIs, location or user identifiers.
 *
 * Exception messages are where such things end up, and free text cannot be recognised reliably by pattern. So a
 * message is never forwarded as written: only the fixed technical messages listed in [SAFE_MESSAGES] and POSIX error
 * codes survive. What identifies a failure is the exception class and the code locations.
 */
object FailureSanitizer {

    const val MAX_STACK = 5800
    private const val MAX_FRAMES = 18
    private const val MAX_CAUSES = 4

    /** Whole-message patterns. The replacement is used when the original has a variable part; null keeps the match. */
    private val SAFE_MESSAGES: List<Pair<Regex, String?>> = listOf(
        // MediaRecorder / MediaCodec native calls
        Regex(
            "(?:start|stop|prepare|pause|resume|release|setAudioSource|setAudioEncoder|setOutputFormat|setOutputFile|" +
                "setAudioChannels|setAudioSamplingRate|setAudioEncodingBitRate) failed\\.?(?:: -?\\d{1,10})?"
        ) to null,
        Regex("(?:Error )?0x[0-9a-fA-F]{1,8}") to null,
        Regex("Failed to instantiate extractor\\.") to null,
        Regex("Failed to allocate a \\d+ byte allocation.*") to "Failed to allocate memory",
        // Model download and verification (ModelDownloader); file names are dropped even though they are app constants
        Regex("Download failed \\(HTTP \\d{3}\\)") to null,
        Regex("Downloaded model failed its integrity check") to null,
        Regex("Extracted \\S+ failed its integrity check") to "Extracted file failed its integrity check",
        Regex("Archive entry \\S+ has an unexpected size") to "Archive entry has an unexpected size",
        Regex("Archive is missing .*") to "Archive is missing a model file",
        Regex("Could not save .*") to "Could not save a model file",
        // Vosk, live transcript, speaker labels
        Regex("Failed to create a (?:model|recognizer)") to null,
        Regex("microphone busy|no buffer size|AudioRecord not initialized|Unexpected diarization sample rate") to null,
        Regex("audio source read failed: -?\\d{1,6}") to null
    )

    private val ERRNO = Regex(
        "\\b(?:ENOSPC|EACCES|ENOENT|EIO|EPERM|EROFS|ENOMEM|EBUSY|EAGAIN|EMFILE|ENFILE|EEXIST|EISDIR|ENOTDIR|EDQUOT|" +
            "EFBIG|EPIPE|EINVAL|EBADF|ENODEV|ENXIO)\\b"
    )
    private val NON_ASCII = Regex("[^\\x20-\\x7E]")
    private val SPACES = Regex("\\s+")

    private val NUMBER = Regex("-?\\d{1,10}")

    private val MODELS: Set<String> by lazy {
        (TranscriptionLanguage.entries.map { it.modelDirName } +
            AsrEngine.entries.mapNotNull { SherpaModels.forEngine(it)?.dirName } + SherpaModels.DIARIZATION_DIR).toSet()
    }
    private val ENGINES: Set<String> by lazy { AsrEngine.entries.map { it.name }.toSet() }
    private val FORMATS: Set<String> by lazy { AudioFormat.entries.map { it.name }.toSet() }
    private val STAGES = setOf("setup", "prepare", "start", "pause", "resume", "download", "extract", "load", "session")
    private val DURATIONS = setOf("lt_10s", "10s_1m", "1_5m", "5_30m", "gt_30m")

    /** Context keys the app may attach, each with the values it may have. Anything else is dropped. */
    private fun allowed(key: String, value: String): Boolean = when (key) {
        "engine" -> value in ENGINES
        "model" -> value in MODELS
        "format" -> value in FORMATS
        "stage" -> value in STAGES
        "duration" -> value in DURATIONS
        "thread" -> value == "main" || value == "background"
        "status", "code", "free_mb" -> NUMBER.matches(value)
        else -> false
    }

    /** The part of an exception message that is safe to send, or "" when none of it is. */
    fun safeDetail(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isEmpty()) return ""
        for ((pattern, replacement) in SAFE_MESSAGES) {
            if (pattern.matches(message)) return replacement ?: message
        }
        val codes = LinkedHashSet<String>()
        ERRNO.findAll(message).forEach { codes.add(it.value) }
        if (message.contains("No space left on device", ignoreCase = true)) codes.add("ENOSPC")
        return codes.joinToString(" ")
    }

    /** "ClassName" or "ClassName: safe detail", or "" when there is no throwable. */
    fun describe(t: Throwable?): String {
        if (t == null) return ""
        val name = t.javaClass.simpleName.ifEmpty { t.javaClass.name }
        val detail = safeDetail(t.message)
        return if (detail.isEmpty()) name else "$name: $detail"
    }

    /** Class names and code locations only, for the throwable and its causes. No messages. */
    fun stack(t: Throwable?): String {
        val sb = StringBuilder()
        var current = t
        var depth = 0
        val seen = HashSet<Throwable>()
        while (current != null && depth < MAX_CAUSES && seen.add(current)) {
            if (depth > 0) sb.append("Caused by: ")
            sb.append(current.javaClass.name).append('\n')
            for (frame in current.stackTrace.take(MAX_FRAMES)) {
                sb.append("\tat ").append(frame.className).append('.').append(frame.methodName)
                    .append('(').append(frame.fileName ?: "Unknown").append(':').append(frame.lineNumber).append(")\n")
            }
            current = current.cause
            depth++
        }
        return sb.toString().take(MAX_STACK)
    }

    fun context(raw: Map<String, String>): Map<String, String> = raw.filter { (key, value) -> allowed(key, value) }

    /** Device strings from the OS: ASCII, short, no control characters. */
    fun device(raw: String?): String = NON_ASCII.replace(raw.orEmpty(), "?").replace(SPACES, " ").trim().take(60)
}

/** Which throwables are expected events rather than defects. */
object FailureClassifier {
    private fun chain(t: Throwable?): Sequence<Throwable> = generateSequence(t) { it.cause }.take(8)

    /** Coroutine or worker cancellation is control flow, never a failure. */
    fun isCancellation(t: Throwable?): Boolean = chain(t).any { it is CancellationException }

    fun isStorageFull(t: Throwable?): Boolean = chain(t).any {
        val m = it.message.orEmpty()
        m.contains("ENOSPC") || m.contains("No space left", ignoreCase = true) || it.javaClass.simpleName == "SQLiteFullException"
    }

    /**
     * The phone is offline or the connection dropped. A bad server certificate is not counted here: that points at the
     * download host (or a network that intercepts it) and is worth knowing about.
     */
    fun isOffline(t: Throwable?): Boolean {
        val causes = chain(t).toList()
        if (causes.any { it is java.security.cert.CertificateException || it is javax.net.ssl.SSLPeerUnverifiedException }) return false
        return causes.any {
            it is java.net.UnknownHostException || it is java.net.SocketException ||
                it is java.net.SocketTimeoutException || it is javax.net.ssl.SSLException ||
                (it is java.net.ProtocolException && it.message.orEmpty().contains("unexpected end of stream", ignoreCase = true))
        }
    }
}

/** Why a transcription ended without any text. */
object TranscriptionFailure {
    fun noTextReason(decodeError: Throwable?, hadAudio: Boolean): ReportReason = when {
        decodeError != null -> ReportReason.DECODE_FAILED
        !hadAudio -> ReportReason.NO_AUDIO
        else -> ReportReason.EMPTY_RESULT
    }
}

data class DeviceInfo(val device: String, val androidRelease: String, val sdk: Int)

/** Builds the JSON body of one report. Every field passes through [FailureSanitizer]. */
object ReportPayload {
    fun failureMessage(t: Throwable?, reason: ReportReason?): String = listOf(
        if (FailureClassifier.isStorageFull(t)) "storage_full" else "",
        reason?.code.orEmpty(),
        FailureSanitizer.describe(t)
    ).filter { it.isNotEmpty() }.joinToString(" | ").ifEmpty { "failure" }

    fun build(
        kind: String,
        version: String,
        flow: ReportFlow,
        t: Throwable?,
        reason: ReportReason?,
        context: Map<String, String>,
        device: DeviceInfo
    ): String {
        val ctx = JSONObject()
        FailureSanitizer.context(context).forEach { (k, v) -> ctx.put(k, v) }
        ctx.put("device", FailureSanitizer.device(device.device))
        ctx.put("android", FailureSanitizer.device(device.androidRelease))
        ctx.put("sdk", device.sdk.toString())
        return JSONObject()
            .put("kind", kind)
            .put("platform", "android")
            .put("version", FailureSanitizer.device(version).ifEmpty { "unknown" })
            .put("flow", flow.code)
            .put("message", failureMessage(t, reason))
            .put("stack", FailureSanitizer.stack(t))
            .put("context", ctx)
            .toString()
    }
}

/**
 * Client-side throttle so a failure loop (or a crash loop) cannot hammer the network or the battery. The history is
 * saved through [save], so the limits hold across app restarts:
 *  - the same flow and message at most once an hour,
 *  - one flow at most [perFlowPerHour] times an hour,
 *  - everything together at most [perMinute] a minute and [perDay] a day (crashes are exempt from the daily cap).
 */
class ReportLimiter(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val perFlowPerHour: Int = 3,
    private val perMinute: Int = 10,
    private val perDay: Int = 30
) {
    private data class Sent(val at: Long, val flow: String, val fingerprint: Int)

    @Synchronized
    fun allow(flow: String, message: String, crash: Boolean = false): Boolean {
        val t = now()
        // Entries from the future (the clock was moved back) are dropped so they cannot block reporting for long.
        val history = parse(load()).filter { t - it.at < DAY_MS && it.at - t < MINUTE_MS }
        val fingerprint = (flow + "|" + message.replace(DIGITS, "#")).hashCode()
        val lastHour = history.filter { t - it.at < HOUR_MS }
        if (lastHour.any { it.fingerprint == fingerprint }) return false
        if (lastHour.count { it.flow == flow } >= perFlowPerHour) return false
        if (lastHour.count { t - it.at < MINUTE_MS } >= perMinute) return false
        if (!crash && history.size >= perDay) return false
        save((history + Sent(t, flow, fingerprint)).joinToString("\n") { "${it.at},${it.flow},${it.fingerprint}" })
        return true
    }

    private fun parse(raw: String?): List<Sent> = raw.orEmpty().split('\n').mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size != 3) return@mapNotNull null
        Sent(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1], parts[2].toIntOrNull() ?: return@mapNotNull null)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS
        val DIGITS = Regex("\\d+")
    }
}
