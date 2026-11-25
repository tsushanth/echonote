package com.kreativekoala.echonote.diagnostics

import org.json.JSONObject

internal class MapStore : ReportStore {
    val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

internal class FakeTransport(var accept: Boolean = true, var error: Exception? = null) : ReportTransport {
    val bodies = mutableListOf<String>()
    override fun post(body: String, timeoutMs: Int): Boolean {
        synchronized(bodies) { bodies.add(body) }
        error?.let { throw it }
        return accept
    }

    val reports: List<JSONObject> get() = synchronized(bodies) { bodies.map { JSONObject(it) } }
}

internal val TEST_DEVICE = DeviceInfo("Google Pixel 9 Pro", "16", 36)

/** An engine that sends on the calling thread, so tests need no waiting. */
internal fun testEngine(
    transport: FakeTransport,
    store: MapStore = MapStore(),
    now: () -> Long = System::currentTimeMillis
) = ReporterEngine("1.15.1", TEST_DEVICE, store, transport, { it.run() }, now = now)

internal fun JSONObject.ctx(key: String): String? = getJSONObject("context").optString(key).ifEmpty { null }
