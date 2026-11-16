package com.kreativekoala.echonote.util

import android.content.Context
import com.kreativekoala.echonote.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object DateFormatting {

    fun formatRelativeDate(context: Context, timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMs

        return when {
            diff < 60_000 -> context.getString(R.string.date_just_now)
            diff < 3_600_000 -> context.getString(R.string.date_minutes_ago, (diff / 60_000).toInt())
            diff < 86_400_000 -> context.getString(R.string.date_hours_ago, (diff / 3_600_000).toInt())
            diff < 604_800_000 -> context.getString(R.string.date_days_ago, (diff / 86_400_000).toInt())
            else -> {
                val format = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                format.format(Date(timestampMs))
            }
        }
    }

    /** Date and time in the phone's own order, month name and 12 or 24 hour clock (not a fixed US pattern). */
    fun formatDateTime(timestampMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(timestampMs))

    fun formatTime(timestampMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestampMs))
}
