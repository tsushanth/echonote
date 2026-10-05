package com.kreativekoala.echonote.service

import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumManagerTest {

    private val manager = PremiumManager()

    @Test
    fun recordingCreationIsNeverLimited() {
        listOf(0, 3, 4, 100, Int.MAX_VALUE).forEach { assertTrue(manager.canCreateRecording(it)) }
    }

    @Test
    fun folderCreationIsNeverLimited() {
        listOf(0, 1, 2, 100, Int.MAX_VALUE).forEach { assertTrue(manager.canCreateFolder(it)) }
    }

    @Test
    fun bookmarkCreationIsNeverLimited() {
        listOf(0, 2, 3, 100, Int.MAX_VALUE).forEach { assertTrue(manager.canCreateBookmark(it)) }
    }

    @Test
    fun transcriptionIsAlwaysAvailable() {
        assertTrue(manager.canUseTranscription())
    }
}
