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
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val MILLIS_PER_SECOND = 1000L
private const val PROCESSING_DELAY_MS = 500L
private const val CLEANUP_DELAY_MS = 1000L

class ScreenshotMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var contentObserver: ContentObserver
    private lateinit var app: ScreenshotApp
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        app = application as ScreenshotApp
        notificationHelper = NotificationHelper(this)

        DebugLogger.info("ScreenshotMonitorService", "Service onCreate() called")
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        setupContentObserver()

        serviceScope.launch {
            DebugLogger.info("ScreenshotMonitorService", "Scanning existing screenshots on service start")
            scanExistingScreenshots()

            // Clean up expired screenshots with deleted files
            val currentTime = System.currentTimeMillis()
            val expired = app.repository.getExpiredScreenshots(currentTime)
                .filter { !java.io.File(it.filePath).exists() }
            expired.forEach { screenshot ->
                app.repository.delete(screenshot)
                DebugLogger.info("ScreenshotMonitorService", "Cleaned up expired screenshot: ${screenshot.fileName}")
            }
        }
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
                DebugLogger.debug("ScreenshotMonitorService", "New media detected: $uri")
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
                        val dateAdded = cursor.getLong(dateIndex) * MILLIS_PER_SECOND

                        DebugLogger.debug("ScreenshotMonitorService", "File detected: $fileName at $filePath")

                        if (isScreenshotFile(filePath)) {
                            DebugLogger.info("ScreenshotMonitorService", "Screenshot file detected: $fileName")
                            delay(PROCESSING_DELAY_MS)

                            val existing = app.repository.getByFilePath(filePath)
                            if (existing == null) {
                                DebugLogger.info("ScreenshotMonitorService", "Processing new screenshot: $fileName")
                                processNewScreenshot(filePath, fileName, fileSize, dateAdded)
                            } else {
                                DebugLogger.debug(
                                    "ScreenshotMonitorService",
                                    "Screenshot already exists in DB: $fileName"
                                )
                            }
                        } else {
                            DebugLogger.debug("ScreenshotMonitorService", "Not a screenshot file: $fileName")
                        }
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                DebugLogger.error("ScreenshotMonitorService", "Error handling new screenshot", e)
            }
        }
    }

    private suspend fun isScreenshotFile(filePath: String): Boolean {
        val lowerPath = filePath.lowercase()

        // Check if it's in the configured screenshot folder
        val configuredFolder = app.preferences.screenshotFolder.first()
        val screenshotFolder = if (configuredFolder.isNotEmpty()) {
            // Decode URI to path
            java.net.URLDecoder.decode(configuredFolder, "UTF-8").let { decoded ->
                when {
                    decoded.contains("primary:") -> decoded.substringAfter("primary:")
                    decoded.contains("tree/") -> {
                        val parts = decoded.substringAfter("tree/").split(":")
                        if (parts.size >= 2) parts[1] else decoded
                    }
                    else -> decoded
                }
            }
        } else {
            // Default
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
        }

        val isInFolder = lowerPath.contains(screenshotFolder.lowercase())
        val hasScreenshotName = lowerPath.contains("screenshot")
        val isImage = lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")

        return (isInFolder || hasScreenshotName) && isImage
    }

    private suspend fun processNewScreenshot(
        filePath: String,
        fileName: String,
        @Suppress("UNUSED_PARAMETER") fileSize: Long,
        createdAt: Long
    ) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            DebugLogger.warning("ScreenshotMonitorService", "File doesn't exist or is empty: $fileName")
            return
        }

        val actualFileSize = file.length()
        val isManualMode = app.preferences.isManualMarkMode.first()
        val mode = if (isManualMode) "MANUAL" else "AUTOMATIC"

        DebugLogger.info("ScreenshotMonitorService", "Processing screenshot in $mode mode: $fileName")

        if (isManualMode) {
            val screenshot = Screenshot(
                filePath = filePath,
                fileName = fileName,
                fileSize = actualFileSize,
                createdAt = createdAt,
                deletionTimestamp = null,
                isMarkedForDeletion = false,
                isKept = false
            )
            val id = app.repository.insert(screenshot)
            DebugLogger.info("ScreenshotMonitorService", "Screenshot inserted to DB with ID: $id (Manual Mode)")

            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("screenshot_id", id)
                putExtra("file_path", filePath)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            DebugLogger.info("ScreenshotMonitorService", "OverlayService started for screenshot ID: $id")
        } else {
            val deletionTime = app.preferences.getDeletionTimeMillisSync()
            val deletionTimestamp = System.currentTimeMillis() + deletionTime

            val screenshot = Screenshot(
                filePath = filePath,
                fileName = fileName,
                fileSize = actualFileSize,
                createdAt = createdAt,
                deletionTimestamp = deletionTimestamp,
                isMarkedForDeletion = true,
                isKept = false
            )
            val id = app.repository.insert(screenshot)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Screenshot inserted to DB with ID: $id (Automatic Mode, marked for deletion)"
            )

            notificationHelper.showScreenshotNotification(id, fileName, deletionTimestamp)
            DebugLogger.info("ScreenshotMonitorService", "Notification shown for screenshot ID: $id")
        }
    }

    private fun scanExistingScreenshots() {
        serviceScope.launch {
            try {
                DebugLogger.info("ScreenshotMonitorService", "Starting scan of existing screenshots")

                val configuredFolder = app.preferences.screenshotFolder.first()
                val screenshotFolder = if (configuredFolder.isNotEmpty()) {
    // Decode URI to path
                java.net.URLDecoder.decode(configuredFolder, "UTF-8").let { decoded ->
                when {
                    decoded.contains("primary:") -> decoded.substringAfter("primary:")
                        decoded.contains("tree/") -> {
                        val parts = decoded.substringAfter("tree/").split(":")
                    if (parts.size >= 2) parts[1] else decoded
                    }
                else -> decoded
    }
    }
    } else {
    // Default
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
    }

    val folder = File(screenshotFolder)
    if (!folder.exists() || !folder.isDirectory) {
    DebugLogger.warning(
    "ScreenshotMonitorService",
    "Screenshot folder doesn't exist: $screenshotFolder"
    )
    return@launch
    }

    val imageFiles = folder.listFiles { file ->
    file.isFile && (file.extension.lowercase() in listOf("png", "jpg", "jpeg"))
    }

    val count = imageFiles?.size ?: 0
    DebugLogger.info("ScreenshotMonitorService", "Found $count existing screenshot files")

    var imported = 0
    imageFiles?.forEach { file ->
    val existing = app.repository.getByFilePath(file.absolutePath)
    if (existing == null && file.exists() && file.length() > 0) {
    val screenshot = Screenshot(
    filePath = file.absolutePath,
    fileName = file.name,
    fileSize = file.length(),
    createdAt = file.lastModified(),
    deletionTimestamp = null,
    isMarkedForDeletion = false,
    isKept = false
    )
    app.repository.insert(screenshot)
    imported++
    }
    }
    DebugLogger.info("ScreenshotMonitorService", "Imported $imported new screenshots from existing files")
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    DebugLogger.error("ScreenshotMonitorService", "Error scanning existing screenshots", e)
    }
    }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.info("ScreenshotMonitorService", "Service onDestroy() called")
        contentResolver.unregisterContentObserver(contentObserver)
        serviceScope.launch {
            delay(CLEANUP_DELAY_MS)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
