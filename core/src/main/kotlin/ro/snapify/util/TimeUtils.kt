package ro.snapify.util

import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("MagicNumber")
object TimeUtils {
    private const val SECONDS_PER_MINUTE = 60

    fun formatTimeRemaining(ms: Long): String {
        if (ms <= 0) return "0s"

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)

        return if (totalMinutes <= 0) {
            "${seconds}s"
        } else {
            val weeks = totalMinutes / (7 * 24 * 60)
            val days = (totalMinutes / (24 * 60)) % 7
            val hours = (totalMinutes / 60) % 24
            val minutes = totalMinutes % 60

            val parts = mutableListOf<String>()
            if (weeks > 0) parts.add("${weeks}w")
            if (days > 0) parts.add("${days}d")
            if (hours > 0) parts.add("${hours}h")
            if (minutes > 0) parts.add("${minutes}m")

            parts.joinToString(" ") { it }
        }
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

    fun formatTime(seconds: Long): String {
        val minutes = seconds / SECONDS_PER_MINUTE
        val secs = seconds % SECONDS_PER_MINUTE
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}

