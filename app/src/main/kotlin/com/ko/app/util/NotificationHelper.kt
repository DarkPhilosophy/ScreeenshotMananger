package com.ko.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ko.app.R
import com.ko.app.receiver.NotificationActionReceiver

object NotificationHelper {

    fun showScreenshotNotification(
        context: Context,
        id: Long,
        fileName: String,
        deletionTimestamp: Long,
        deletionTimeMillis: Long = 60_000L,
        isManualMode: Boolean = false
    ) {
        createNotificationChannel(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_KEEP
            putExtra(NotificationActionReceiver.EXTRA_SCREENSHOT_ID, id)
        }
        val keepIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DELETE
            putExtra(NotificationActionReceiver.EXTRA_SCREENSHOT_ID, id)
            putExtra(NotificationActionReceiver.EXTRA_IS_MANUAL_MODE, isManualMode)
            putExtra(NotificationActionReceiver.EXTRA_DELETION_TIME_MILLIS, deletionTimeMillis)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt() + 1000,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remainingTime = deletionTimestamp - System.currentTimeMillis()
        val remainingSeconds = remainingTime / 1000

        // Format button text based on mode
        val deletionTimeMinutes = (deletionTimeMillis / (1000 * 60)).toInt()
        val keepButtonText: String
        val deleteButtonText: String
        val notificationTitle: String
        val notificationText: String

        if (isManualMode) {
            // Manual mode: Keep = mark as kept, Delete = schedule deletion
            keepButtonText = "Keep"
            deleteButtonText =
                if (deletionTimeMinutes > 0) "Delete in ${deletionTimeMinutes}m" else "Delete in 1m"
            notificationTitle = fileName
            notificationText = "Choose action"
        } else {
            // Automatic mode: Keep = mark as kept (prevent auto-deletion), Delete = immediate delete
            keepButtonText = "Keep"
            deleteButtonText = "Delete Now"
            notificationTitle = fileName
            notificationText = "Auto-delete in ${remainingSeconds}s"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCREENSHOT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, keepButtonText, keepIntent)
            .addAction(R.drawable.ic_launcher_foreground, deleteButtonText, deletePendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_SCREENSHOT,
                "Screenshot Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for screenshot deletion"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showErrorNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCREENSHOT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
    }

    

    fun showCleanupNotification(context: Context, count: Int) {
        createNotificationChannel(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCREENSHOT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Cleanup Completed")
            .setContentText("Deleted $count expired screenshots")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(CLEANUP_NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}

private const val CHANNEL_ID_SCREENSHOT = "screenshot_channel"
private const val ERROR_NOTIFICATION_ID = 9999
private const val CLEANUP_NOTIFICATION_ID = 9997

