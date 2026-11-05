package com.kreativekoala.echonote.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsEnginePickerTest {
    @Test fun googleFirstThenSamsungThenOthersInSystemOrder() {
        val order = TtsEnginePicker.order(listOf("com.listenai.voice", "com.samsung.SMT", "org.other.tts", "com.google.android.tts"))
        assertEquals(listOf("com.google.android.tts", "com.samsung.SMT", "com.listenai.voice", "org.other.tts"), order)
    }

    @Test fun theDefaultEngineSettingIsNotConsultedSoAThirdPartyDefaultNeverWinsOverGoogle() {
        assertEquals("com.google.android.tts", TtsEnginePicker.order(listOf("com.listenai.voice", "com.google.android.tts")).first())
    }

    @Test fun samsungWinsWhenGoogleIsAbsent() {
        assertEquals(listOf("com.samsung.SMT", "x.y"), TtsEnginePicker.order(listOf("x.y", "com.samsung.SMT")))
    }

    @Test fun noEnginesGivesAnEmptyList() { assertEquals(emptyList<String>(), TtsEnginePicker.order(emptyList())) }

    @Test fun duplicatesAreRemoved() {
        assertEquals(listOf("com.google.android.tts"), TtsEnginePicker.order(listOf("com.google.android.tts", "com.google.android.tts")))
    }
}
