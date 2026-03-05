package com.kreativekoala.echonote.util

import androidx.compose.ui.graphics.Color

fun Color.Companion.fromHex(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    val colorLong = cleanHex.toLong(16)
    return when (cleanHex.length) {
        6 -> Color(0xFF000000 or colorLong)
        8 -> Color(colorLong)
        else -> Color.Gray
    }
}
