package com.ko.app.util

import java.util.concurrent.TimeUnit

@Suppress("MagicNumber")
object TimeUtils {
    private const val SECONDS_PER_MINUTE = 60

    fun formatTimeRemaining(ms: Long): String {
        if (ms <= 0) return "0s"
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
    }

    fun formatDeletionTime(ms: Long): String {
        if (ms <= 0) return "Instant"
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / SECONDS_PER_MINUTE
        val days = hours / 24

        return when {
            days > 0 -> "${days} day${if (days > 1) "s" else ""}"
            hours > 0 -> "${hours} hour${if (hours > 1) "s" else ""}"
            minutes > 0 -> "${minutes} minute${if (minutes > 1) "s" else ""}"
            else -> "${seconds} second${if (seconds > 1) "s" else ""}"
        }
    }
}

