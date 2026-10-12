package com.kreativekoala.echonote.service

import android.app.ActivityManager
import android.content.Context

object DeviceMemory {
    /** Total RAM the system reports, in bytes (a "6 GB" phone reports about 5.7e9). */
    fun totalBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.totalMem
    }
}
