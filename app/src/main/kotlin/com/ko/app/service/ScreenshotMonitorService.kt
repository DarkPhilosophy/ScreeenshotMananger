package com.ko.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.ko.app.ScreenshotApp
import com.ko.app.data.entity.Screenshot
import com.ko.app.ui.MainActivity
import com.ko.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ScreenshotMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var contentObserver: ContentObserver
    private lateinit var app: ScreenshotApp
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        app = application as ScreenshotApp
        notificationHelper = NotificationHelper(this)

        startForeground(NOTIFICATION_ID, createForegroundNotification())
        setupContentObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ScreenshotApp.CHANNEL_ID_SERVICE)
            .setContentTitle("Screenshot Monitor Active")
            .setContentText("Monitoring screenshots for automatic deletion")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { handleNewScreenshot(it) }
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.registerContentObserver(uri, true, contentObserver)
    }

    private fun handleNewScreenshot(uri: Uri) {
        serviceScope.launch {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED
                )

                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                        val filePath = cursor.getString(dataIndex)
                        val fileName = cursor.getString(nameIndex)
                        val fileSize = cursor.getLong(sizeIndex)
                        val dateAdded = cursor.getLong(dateIndex) * 1000

                        if (isScreenshotFile(filePath)) {
                            delay(500)

                            val existing = app.repository.getByFilePath(filePath)
                            if (existing == null) {
                                processNewScreenshot(filePath, fileName, fileSize, dateAdded)
                            }
                        }
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught", "PrintStackTrace") e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isScreenshotFile(filePath: String): Boolean {
        val lowerPath = filePath.lowercase()
        val screenshotFolder = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        ).absolutePath + "/Screenshots"

        return (lowerPath.contains("screenshot") || lowerPath.contains(screenshotFolder.lowercase())) &&
                (lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg"))
    }

    private suspend fun processNewScreenshot(
        filePath: String,
        fileName: String,
        fileSize: Long,
        createdAt: Long
    ) {
        val file = File(filePath)
        if (!file.exists()) return

        val isManualMode = app.preferences.isManualMarkMode.first()

        if (isManualMode) {
            val screenshot = Screenshot(
                filePath = filePath,
                fileName = fileName,
                fileSize = fileSize,
                createdAt = createdAt,
                deletionTimestamp = null,
                isMarkedForDeletion = false,
                isKept = false
            )
            val id = app.repository.insert(screenshot)

            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("screenshot_id", id)
                putExtra("file_path", filePath)
            }
            startService(intent)
        } else {
            val deletionTime = app.preferences.getDeletionTimeMillisSync()
            val deletionTimestamp = System.currentTimeMillis() + deletionTime

            val screenshot = Screenshot(
                filePath = filePath,
                fileName = fileName,
                fileSize = fileSize,
                createdAt = createdAt,
                deletionTimestamp = deletionTimestamp,
                isMarkedForDeletion = true,
                isKept = false
            )
            val id = app.repository.insert(screenshot)

            notificationHelper.showScreenshotNotification(id, fileName, deletionTimestamp)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
        serviceScope.launch {
            delay(1000)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
