package com.kreativekoala.echonote.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Sends crashes and handled failures to the developer's failure-report endpoint. No third-party code.
 *
 * What is sent: app version, Android version, device model, free storage in MB, a stable flow name, a short reason
 * code, the exception class and code locations. Never audio, transcripts, search text, recording titles, file names
 * or paths, location or user identifiers: everything passes through [FailureSanitizer].
 *
 * It does nothing at all, and installs no crash handler, unless the build has an ingest key, is not a debug build and
 * is not running under a test runner or a test lab ([ReportGate]).
 */
object FailureReporter {
    private const val ENDPOINT = "https://app-failure-reporter.t-sushanth.workers.dev/v1/report"
    private const val PREFS = "failure_reporter"
    private const val QUEUE = 20

    @Volatile private var engine: ReporterEngine? = null

    fun init(context: Context, ingestKey: String, versionName: String, debugBuild: Boolean) =
        init(context, ingestKey, versionName, debugBuild, ::isTestEnvironment) { key ->
            ReportTransport { body, timeoutMs -> post(key, body, timeoutMs) }
        }

    /** [init] with the environment check and the network replaced, so tests can arm the reporter without either. */
    internal fun init(
        context: Context,
        ingestKey: String,
        versionName: String,
        debugBuild: Boolean,
        testEnvironment: (Context) -> Boolean,
        transportFor: (key: String) -> ReportTransport
    ) {
        try {
            val key = ingestKey.trim()
            if (!ReportGate.allows(key, debugBuild) { testEnvironment(context) }) return
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val executor = ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(QUEUE),
                { r -> Thread(r, "failure-reporter").apply { isDaemon = true } },
                ThreadPoolExecutor.DiscardPolicy() // a flood of failures is dropped, never queued without bound
            )
            engine = ReporterEngine(
                version = versionName,
                device = DeviceInfo("${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE.orEmpty(), Build.VERSION.SDK_INT),
                store = object : ReportStore {
                    override fun read(key: String): String? = prefs.getString(key, null)
                    override fun write(key: String, value: String?) { prefs.edit().putString(key, value).commit() }
                },
                transport = transportFor(key),
                executor = executor,
                freeMb = { appContext.filesDir.usableSpace / (1024 * 1024) }
            ).also { it.start() }
        } catch (_: Throwable) {
            // Reporting must never be the reason the app fails to start.
        }
    }

    /** A handled failure. [context] keeps only the keys and values [FailureSanitizer.context] accepts. */
    fun failure(flow: ReportFlow, t: Throwable? = null, reason: ReportReason? = null, context: Map<String, String> = emptyMap()) {
        engine?.failure(flow, t, reason, context)
    }

    /** Lets unit tests observe what call sites report. */
    internal fun replaceEngineForTest(replacement: ReporterEngine?) { engine = replacement }

    /** Instrumentation tests, JUnit, monkey runs and test labs (including Google Play's pre-launch report). */
    private fun isTestEnvironment(context: Context): Boolean {
        fun check(block: () -> Boolean) = try { block() } catch (_: Throwable) { false }
        return check { hasClass("androidx.test.runner.AndroidJUnitRunner") } ||
            check { hasClass("org.junit.runner.JUnitCore") } ||
            check { ActivityManager.isUserAMonkey() } ||
            check { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityManager.isRunningInUserTestHarness() } ||
            check { Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true" }
    }

    private fun hasClass(name: String): Boolean = try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }

    private fun post(key: String, body: String, timeoutMs: Int): Boolean {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Report-Key", key)
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }
}
