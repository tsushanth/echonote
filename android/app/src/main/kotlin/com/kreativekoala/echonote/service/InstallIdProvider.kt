package com.kreativekoala.echonote.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * A random per-install UUID, generated once and reused afterward. Sent with each
 * opt-in voice-data contribution upload, and is the id that would later authorize
 * a deletion request (mirrors VoxKey's own install-id pattern).
 */
class InstallIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "contribution_prefs"
        private const val KEY_INSTALL_ID = "install_id"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getOrCreateInstallId(): String {
        return prefs.getString(KEY_INSTALL_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_INSTALL_ID, it).apply()
            }
    }
}
