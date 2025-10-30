package com.ko.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ko.app.ScreenshotApp
import com.ko.app.receiver.NotificationActionReceiver
import com.ko.app.ui.MainActivity
import java.util.concurrent.TimeUnit

private const val HOURS_IN_DAY = 24L
private const val MINUTES_IN_HOUR = 60L
private const val SECONDS_IN_MINUTE = 60L

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showScreenshotNotification(screenshotId: Long, fileName: String, deletionTimestamp: Long) {
        val timeRemaining = deletionTimestamp - System.currentTimeMillis()
        val timeText = formatTimeRemaining(timeRemaining)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            screenshotId.toInt(),
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val keepIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_KEEP
            putExtra("screenshot_id", screenshotId)
        }
        val keepPendingIntent = PendingIntent.getBroadcast(
            context,
            screenshotId.toInt(),
            keepIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, ScreenshotApp.CHANNEL_ID_SCREENSHOT)
            .setContentTitle("Screenshot will be deleted")
            .setContentText("$fileName - $timeText remaining")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                "Keep",
                keepPendingIntent
            )
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(screenshotId.toInt(), notification)
    }

    fun updateScreenshotNotification(screenshotId: Long, fileName: String, deletionTimestamp: Long) {
        showScreenshotNotification(screenshotId, fileName, deletionTimestamp)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun formatTimeRemaining(millis: Long): String {
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

    companion object {
        const val NOTIFICATION_ID_BASE = 2000
    }
}
