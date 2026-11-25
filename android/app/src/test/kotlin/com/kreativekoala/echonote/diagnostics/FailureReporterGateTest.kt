package com.kreativekoala.echonote.diagnostics

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FailureReporterGateTest {

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private val transport = FakeTransport()
    private val keysUsed = mutableListOf<String>()

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        FailureReporter.replaceEngineForTest(null)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        FailureReporter.replaceEngineForTest(null)
    }

    /** Starts the real Android wrapper as it would on a real phone, but with a fake network. */
    private fun initOnARealPhone(context: Context, key: String, debugBuild: Boolean) =
        FailureReporter.init(context, key, "1.15.1", debugBuild, testEnvironment = { false }) {
            keysUsed.add(it)
            transport
        }

    /** A context that fails on any use: an inactive reporter must not touch it. */
    private fun assertInert(key: String, debugBuild: Boolean) {
        val context = mockk<Context>()
        initOnARealPhone(context, key, debugBuild)
        assertSame("no crash handler may be installed", originalHandler, Thread.getDefaultUncaughtExceptionHandler())
        verify { context wasNot Called }
        FailureReporter.failure(ReportFlow.TRANSCRIPTION_RUN, reason = ReportReason.EMPTY_RESULT) // no-op, must not throw
        assertEquals(emptyList<String>(), keysUsed)
        assertEquals(emptyList<String>(), transport.bodies)
    }

    @Test
    fun withoutAKeyTheReporterIsCompletelyInactive() {
        assertInert(key = "", debugBuild = false)
        assertInert(key = "  \n", debugBuild = false)
    }

    @Test
    fun aDebugBuildNeverReports() = assertInert(key = "a-key", debugBuild = true)

    @Test
    fun withAKeyOnARealPhoneTheCrashHandlerIsInstalled() {
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        initOnARealPhone(context, " a-key ", debugBuild = false)
        assertNotSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
        assertEquals(listOf("a-key"), keysUsed)
    }

    @Test
    fun aTestRunnerNeverReportsEvenWithAKeyInAReleaseBuild() {
        // The shipped environment check: JUnit is on the classpath here, which is one of its signals.
        val context = mockk<Context>(relaxed = true)
        FailureReporter.init(context, "a-key", "1.15.1", false)
        assertSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
        verify(exactly = 0) { context.getSharedPreferences(any(), any()) }
        verify(exactly = 0) { context.applicationContext }
    }

    @Test
    fun theGateNeedsAKeyAReleaseBuildAndARealDevice() {
        assertTrue(ReportGate.allows("a-key", debugBuild = false) { false })
        assertFalse(ReportGate.allows("", debugBuild = false) { false })
        assertFalse(ReportGate.allows("a-key", debugBuild = true) { false })
        assertFalse(ReportGate.allows("a-key", debugBuild = false) { true })
        // The environment is not even inspected without a key.
        assertFalse(ReportGate.allows("", debugBuild = false) { error("must not be asked") })
    }
}
