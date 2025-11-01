package com.ko.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ko.app.ScreenshotApp
import com.ko.app.receiver.NotificationActionReceiver
import com.ko.app.ui.MainActivity
import com.ko.app.util.TimeUtils

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showScreenshotNotification(screenshotId: Long, fileName: String, deletionTimestamp: Long) {
        val timeRemaining = deletionTimestamp - System.currentTimeMillis()
        val timeText = TimeUtils.formatTimeRemaining(timeRemaining)

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



    fun showErrorNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, ScreenshotApp.CHANNEL_ID_SCREENSHOT)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
