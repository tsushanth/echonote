package com.kreativekoala.echonote.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatting {

    fun formatRelativeDate(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMs

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> {
                val format = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                format.format(Date(timestampMs))
            }
        }
    }

    fun formatDateTime(timestampMs: Long): String {
        val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return format.format(Date(timestampMs))
    }
}
