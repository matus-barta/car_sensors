package com.anonymus09.carsensors.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0

    return when {
        mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun formatTimestamp(millis: Long?, fallback: String): String =
    millis?.let { SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date(it)) } ?: fallback
