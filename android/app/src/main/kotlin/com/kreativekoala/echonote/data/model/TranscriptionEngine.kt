package com.kreativekoala.echonote.data.model

/** Which speech recognizer turns a recording into text. */
enum class TranscriptionEngine(val displayName: String, val summary: String) {
    FAST("Fast", "Vosk. Small download, lower accuracy."),
    ACCURATE("Accurate", "A larger model chosen for your language. 240-375 MB download, slower.")
}
