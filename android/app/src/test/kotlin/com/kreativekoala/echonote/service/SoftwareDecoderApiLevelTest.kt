package com.kreativekoala.echonote.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every transcription crashed on Android 8 and 9: choosing a decoder called `MediaCodecInfo.isSoftwareOnly`, which
 * exists from Android 10. Codecs must only be asked from API 29.
 */
class SoftwareDecoderApiLevelTest {
    @Test fun codecsAreNotAskedBeforeAndroid10() {
        for (sdk in 26..28) assertFalse("API $sdk", TranscriptionService.canFindSoftwareDecoder(sdk))
    }

    @Test fun codecsAreAskedFromAndroid10() {
        assertTrue(TranscriptionService.canFindSoftwareDecoder(29))
        assertTrue(TranscriptionService.canFindSoftwareDecoder(36))
    }
}
