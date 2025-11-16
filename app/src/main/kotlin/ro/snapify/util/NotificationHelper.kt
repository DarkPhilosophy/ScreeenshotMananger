package ro.snapify.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import ro.snapify.R
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.receiver.NotificationActionReceiver

object NotificationHelper {

    private val dismissedNotifications = mutableSetOf<Long>()

    fun markNotificationDismissed(id: Long) {
        dismissedNotifications.add(id)
    }

    suspend fun showScreenshotNotification(
        context: Context,
        id: Long,
        fileName: String,
        filePath: String,
        deletionTimestamp: Long,
        deletionTimeMillis: Long = 60_000L,
        isManualMode: Boolean = false,
        preferences: AppPreferences? = null
    ) {
        // Check if notifications are enabled in settings
        val notificationsEnabled = preferences?.notificationsEnabled?.first() ?: true

        if (!notificationsEnabled) {
            DebugLogger.info(
                "NotificationHelper",
                "Notifications disabled in settings, skipping notification for screenshot ID: $id"
            )
            return
        }

        // Check if notification permission is granted (Android 13+)
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Assume granted for older versions
        }

        if (!hasNotificationPermission) {
            DebugLogger.warning(
                "NotificationHelper",
                "POST_NOTIFICATIONS permission not granted, cannot show notification for screenshot ID: $id"
            )
            return
        }

        if (dismissedNotifications.contains(id)) {
            DebugLogger.info(
                "NotificationHelper",
                "Notification for screenshot ID: $id was dismissed, not recreating"
            )
            return
        }

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
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt() + 1000,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_SCREENSHOT_ID, id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt() + 2000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remainingTime = deletionTimestamp - System.currentTimeMillis()
        val remainingSeconds = remainingTime / 1000

        // Notification actions are always immediate: Keep = mark as kept, Delete Now = immediate delete
        val keepButtonText: String
        val deleteButtonText: String
        val notificationTitle: String
        val notificationText: String

        // Notification actions are always immediate: Keep = mark as kept, Delete Now = immediate delete
        keepButtonText = "Keep"
        deleteButtonText = "Delete Now"
        notificationTitle = fileName

        if (isManualMode) {
            notificationText = "Choose action"
        } else {
            notificationText = "Auto-delete in ${remainingSeconds}s"
        }

        // Load screenshot bitmap for notification
        val screenshotBitmap = try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4 // Downsample to reduce memory usage
            }
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_SCREENSHOT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, keepButtonText, keepIntent)
            .addAction(R.drawable.ic_launcher_foreground, deleteButtonText, deletePendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        // Set big picture style if bitmap loaded successfully
        if (screenshotBitmap != null) {
            notificationBuilder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(screenshotBitmap)
                    .setBigContentTitle(notificationTitle)
                    .setSummaryText(notificationText)
            )
        }

        val notification = notificationBuilder.build()

        notificationManager.notify(id.toInt(), notification)
        DebugLogger.info(
            "NotificationHelper",
            "Notification shown for screenshot ID: $id, title: $notificationTitle, manualMode: $isManualMode"
        )
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

