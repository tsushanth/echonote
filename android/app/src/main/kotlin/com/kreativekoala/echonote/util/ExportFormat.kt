package com.kreativekoala.echonote.util

sealed class ExportFormat {
    object TXT : ExportFormat()
    object SRT : ExportFormat()
    object VTT : ExportFormat()
}
