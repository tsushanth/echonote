package com.kreativekoala.echonote.diagnostics

import java.util.concurrent.Executor

/** Small durable key-value storage. [write] with null removes the key and must be on disk when it returns. */
interface ReportStore {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

fun interface ReportTransport {
    /** True when the server accepted the report. May throw. */
    fun post(body: String, timeoutMs: Int): Boolean
}

/**
 * Sends handled failures in the background and uncaught crashes before the process dies. A crash report is saved
 * first and sent again on the next launch if it did not get through. Nothing here throws into the caller.
 */
class ReporterEngine(
    private val version: String,
    private val device: DeviceInfo,
    private val store: ReportStore,
    private val transport: ReportTransport,
    private val executor: Executor,
    private val freeMb: () -> Long? = { null },
    now: () -> Long = System::currentTimeMillis,
    private val crashWaitMs: Long = 3000
) {
    private val limiter = ReportLimiter({ store.read(KEY_HISTORY) }, { store.write(KEY_HISTORY, it) }, now)

    fun start() {
        flushPendingCrash()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            try {
                crash(thread.name, t)
            } catch (_: Throwable) {
            } finally {
                previous?.uncaughtException(thread, t)
            }
        }
    }

    /** A handled failure: a user-facing flow broke but the app is alive. Cancellations and offline errors are ignored. */
    fun failure(flow: ReportFlow, t: Throwable? = null, reason: ReportReason? = null, context: Map<String, String> = emptyMap()) {
        try {
            if (FailureClassifier.isCancellation(t) || FailureClassifier.isOffline(t)) return
            executor.execute {
                try {
                    if (!limiter.allow(flow.code, ReportPayload.failureMessage(t, reason))) return@execute
                    val free = freeMb()?.let { mapOf("free_mb" to it.toString()) }.orEmpty()
                    transport.post(ReportPayload.build("failure", version, flow, t, reason, context + free, device), SEND_TIMEOUT_MS)
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun crash(threadName: String, t: Throwable) {
        if (!limiter.allow(ReportFlow.CRASH.code, ReportPayload.failureMessage(t, null), crash = true)) return
        val thread = if (threadName == "main") "main" else "background"
        val body = ReportPayload.build("crash", version, ReportFlow.CRASH, t, null, mapOf("thread" to thread), device)
        store.write(KEY_PENDING_CRASH, body) // first: the process is about to die and the network may be down
        // A thread of its own: the crashing thread may be the main thread, which is not allowed to open a socket.
        val sender = Thread({ sendPending(body, crashWaitMs.toInt()) }, "failure-reporter-crash")
        sender.isDaemon = true
        sender.start()
        sender.join(crashWaitMs)
    }

    private fun flushPendingCrash() {
        try {
            executor.execute {
                try {
                    store.read(KEY_PENDING_CRASH)?.let { sendPending(it, SEND_TIMEOUT_MS) }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun sendPending(body: String, timeoutMs: Int) {
        try {
            // Remove only what was sent: a newer crash may have replaced it meanwhile.
            if (transport.post(body, timeoutMs) && store.read(KEY_PENDING_CRASH) == body) store.write(KEY_PENDING_CRASH, null)
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val KEY_PENDING_CRASH = "pending_crash"
        const val KEY_HISTORY = "sent_history"
        private const val SEND_TIMEOUT_MS = 8000
    }
}
