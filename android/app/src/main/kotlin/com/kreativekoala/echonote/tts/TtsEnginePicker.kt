package com.kreativekoala.echonote.tts

/**
 * Chooses which installed text-to-speech engine to try first. The phone's default engine setting is never used: it can
 * be a third-party or network engine, and the app promises audio and text stay on the phone.
 */
object TtsEnginePicker {
    const val GOOGLE = "com.google.android.tts"
    const val SAMSUNG = "com.samsung.SMT"

    /** Google first, then Samsung, then every other installed engine in the order the system listed them. */
    fun order(installed: List<String>): List<String> {
        val distinct = installed.distinct()
        val preferred = listOf(GOOGLE, SAMSUNG).filter { it in distinct }
        return preferred + distinct.filter { it !in preferred }
    }
}
