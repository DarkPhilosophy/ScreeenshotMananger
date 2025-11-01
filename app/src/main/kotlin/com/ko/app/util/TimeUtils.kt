package com.ko.app.util

import java.util.concurrent.TimeUnit

object TimeUtils {

    private const val HOURS_IN_DAY = 24L
    private const val MINUTES_IN_HOUR = 60L
    private const val SECONDS_IN_MINUTE = 60L

    fun formatTimeRemaining(millis: Long): String {
        if (millis <= 0) return "Expired"

        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % HOURS_IN_DAY
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % MINUTES_IN_HOUR
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % SECONDS_IN_MINUTE

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
