package com.kreativekoala.echonote.data.model

/** Which speech recognizer turns a recording into text. */
enum class TranscriptionEngine(val displayName: String, val summary: String) {
    FAST("Fast", "Vosk. Small download, lower accuracy."),
    ACCURATE("Accurate", "Whisper. About 160 MB download, slower, much better text.")
}
