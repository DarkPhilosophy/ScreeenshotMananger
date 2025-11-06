package com.ko.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.ko.app.ScreenshotApp
import com.ko.app.data.entity.Screenshot
import com.ko.app.ui.MainActivity
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import com.ko.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val MILLIS_PER_SECOND = 1000L
private const val PROCESSING_DELAY_MS = 500L
private const val CLEANUP_DELAY_MS = 1000L

@AndroidEntryPoint
class ScreenshotMonitorService : Service() {

    @Inject
    lateinit var repository: com.ko.app.data.repository.ScreenshotRepository

    @Inject
    lateinit var refreshFlow: MutableSharedFlow<Unit>

    @Inject
    lateinit var preferences: com.ko.app.data.preferences.AppPreferences

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var contentObserver: ContentObserver
    private var deletionCheckJob: Job? = null

    // Individual deletion timers for each marked screenshot
    private val deletionJobs = mutableMapOf<Long, Job>()

    // Deduplication for screenshot notifications - track recent notifications
    private val recentNotifications = mutableMapOf<String, Long>() // filePath -> timestamp
    private val NOTIFICATION_DEDUPE_WINDOW = 5000L // 5 seconds window

    override fun onCreate() {
        super.onCreate()

        DebugLogger.info("ScreenshotMonitorService", "Service onCreate() called")

        // For foreground service, we must call startForeground within a few seconds
        // So call it immediately, even if we'll stop due to missing permissions
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        // Check permissions after starting foreground to avoid system timeout
        if (PermissionUtils.getMissingPermissions(this).isNotEmpty()) {
            DebugLogger.error(
                "ScreenshotMonitorService",
                "Required permissions not granted, stopping service"
            )
            stopSelf()
            return
        }

        setupContentObserver()
        startDeletionCheckTimer()

        serviceScope.launch {
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Scanning existing screenshots on service start"
            )
            scanExistingScreenshots()

            // Clean up expired screenshots whose files are gone
            val currentTime = System.currentTimeMillis()
            val expired = repository.getExpiredScreenshots(currentTime)
            expired.filter { screenshot ->
                screenshot.contentUri?.let { uriStr ->
                    try {
                        val uri = uriStr.toUri()
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(MediaStore.Images.Media._ID),
                            null,
                            null,
                            null
                        )
                        cursor?.use { it.count > 0 } ?: !File(screenshot.filePath).exists()
                    } catch (_: Exception) {
                        !File(screenshot.filePath).exists()
                    }
                } ?: !File(screenshot.filePath).exists()
            }.forEach { screenshot ->
                repository.delete(screenshot)
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Cleaned up expired screenshot: ${'$'}{screenshot.fileName}"
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.info("ScreenshotMonitorService", "onStartCommand called")

        if (intent?.getBooleanExtra("rescan", false) == true) {
            serviceScope.launch {
                DebugLogger.info("ScreenshotMonitorService", "Rescanning triggered from intent")
                scanExistingScreenshots()
            }
        }

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
            .build()
    }

    private fun setupContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { handleNewScreenshot(it) }
            }
        }

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.registerContentObserver(uri, true, contentObserver)
        DebugLogger.info("ScreenshotMonitorService", "Content observer registered for $uri")
    }

    private fun handleNewScreenshot(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )
        serviceScope.launch {
            try {
                DebugLogger.debug("ScreenshotMonitorService", "New media detected: $uri")
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val fileName =
                            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()

                        val filePath = if (dataIndex != -1) cursor.getString(dataIndex) else null
                        val fileSize = cursor.getLong(sizeIndex)
                        val dateAdded = cursor.getLong(dateIndex) * MILLIS_PER_SECOND

                        if (filePath?.contains(".pending") == true) {
                            DebugLogger.debug(
                                "ScreenshotMonitorService",
                                "Ignoring pending file: $fileName"
                            )
                            return@use
                        }

                        val isScreenshot = if (filePath != null) {
                            isScreenshotFile(filePath)
                        } else {
                            fileName.lowercase().contains("screenshot")
                        }

                        if (isScreenshot) {
                            DebugLogger.info(
                                "ScreenshotMonitorService",
                                "Screenshot file detected: $fileName"
                            )
                            delay(PROCESSING_DELAY_MS)

                            val existing = filePath?.let { repository.getByFilePath(it) }
                            DebugLogger.debug(
                                "ScreenshotMonitorService",
                                "Existing screenshot for $filePath: ${existing?.id}"
                            )
                            if (existing == null) {
                                // Check for recent notification to prevent duplicates (for both manual and automatic modes)
                                val currentTime = System.currentTimeMillis()
                                val dedupeKey = filePath ?: contentUri ?: "unknown_${currentTime}"
                                val lastNotificationTime = recentNotifications[dedupeKey] ?: 0L

                                if (currentTime - lastNotificationTime > NOTIFICATION_DEDUPE_WINDOW) {
                                    // Update recent notifications
                                    recentNotifications[dedupeKey] = currentTime

                                    // Clean up old entries (keep only recent ones)
                                    recentNotifications.entries.removeIf { (_, timestamp) ->
                                        currentTime - timestamp > NOTIFICATION_DEDUPE_WINDOW
                                    }

                                    DebugLogger.info(
                                        "ScreenshotMonitorService",
                                        "Processing new screenshot: $fileName"
                                    )
                                    processNewScreenshot(
                                        filePath,
                                        contentUri,
                                        fileName,
                                        fileSize,
                                        dateAdded
                                    )
                                } else {
                                    DebugLogger.info(
                                        "ScreenshotMonitorService",
                                        "Skipping duplicate notification for $fileName (key: $dedupeKey, last: $lastNotificationTime, current: $currentTime)"
                                    )
                                }
                            } else {
                                DebugLogger.debug(
                                    "ScreenshotMonitorService",
                                    "Screenshot already exists in DB: $fileName"
                                )
                            }
                        } else {
                            DebugLogger.debug(
                                "ScreenshotMonitorService",
                                "Not a screenshot file: $fileName"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.error("ScreenshotMonitorService", "Error handling new screenshot", e)
            }
        }
    }

    private suspend fun isScreenshotFile(filePath: String): Boolean {
        val lowerPath = filePath.lowercase()

        // Check if it's in the configured screenshot folder
        val configuredUri = preferences.screenshotFolderUri.first()
        val screenshotFolder = run {
            if (configuredUri.isNotEmpty()) {
                try {
                    val decoded = java.net.URLDecoder.decode(configuredUri, "UTF-8")
                    when {
                        decoded.contains("primary:") -> Environment.getExternalStorageDirectory().absolutePath + "/" + decoded.substringAfter(
                            "primary:"
                        )

                        decoded.contains("tree/") -> {
                            val parts = decoded.substringAfter("tree/").split(":")
                            if (parts.size >= 2) Environment.getExternalStorageDirectory().absolutePath + "/" + parts[1] else decoded
                        }

                        else -> decoded
                    }
                } catch (_: Exception) {
                    configuredUri
                }
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
            }
        }

        val isInFolder = lowerPath.contains(screenshotFolder.lowercase())
        val hasScreenshotName = lowerPath.contains("screenshot")
        val isImage =
            lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")

        return (isInFolder || hasScreenshotName) && isImage
    }

    private suspend fun processNewScreenshot(
        filePath: String?,
        contentUri: String?,
        fileName: String,
        fileSize: Long,
        createdAt: Long
    ) {
        // Validate existence: prefer contentUri, fallback to file path
        val exists = contentUri?.let { uriStr ->
            try {
                val uri = uriStr.toUri()
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize > 0
                } ?: false
            } catch (_: Exception) {
                false
            }
        } ?: run {
            filePath?.let { path ->
                val f = File(path)
                f.exists() && f.length() > 0L
            } ?: false
        }

        if (!exists) {
            DebugLogger.warning(
                "ScreenshotMonitorService",
                "File doesn't exist or is empty: $fileName"
            )
            return
        }

        val actualFileSize = filePath?.let { File(it).length() } ?: fileSize
        val isManualMode = preferences.isManualMarkMode.first()
        val mode = if (isManualMode) "MANUAL" else "AUTOMATIC"
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Processing screenshot in $mode mode: $fileName"
        )

        if (isManualMode) {
            val screenshot = Screenshot(
                filePath = filePath ?: "",
                fileName = fileName,
                fileSize = actualFileSize,
                createdAt = createdAt,
                deletionTimestamp = null,
                isKept = false,
                contentUri = contentUri
            )
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Attempting to insert screenshot: ${screenshot.fileName}"
            )
            val id = repository.insert(screenshot)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Screenshot inserted to DB with ID: $id (Manual Mode)"
            )
            if (id <= 0) {
                DebugLogger.error(
                    "ScreenshotMonitorService",
                    "Failed to insert screenshot, invalid ID: $id"
                )
                return
            }

            // Show overlay for manual mode
            val overlayIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("screenshot_id", id)
                putExtra("file_path", filePath)
            }
            startService(overlayIntent)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Overlay shown for manual mode screenshot ID: $id"
            )

            // Notify UI to refresh
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Emitting refreshFlow after manual mode screenshot"
            )
            refreshFlow.tryEmit(Unit)
        } else {
            val deletionTime = preferences.deletionTimeMillis.first()
            val deletionTimestamp = System.currentTimeMillis() + deletionTime

            val screenshot = Screenshot(
                filePath = filePath ?: "",
                fileName = fileName,
                fileSize = actualFileSize,
                createdAt = createdAt,
                deletionTimestamp = deletionTimestamp,
                isKept = false,
                contentUri = contentUri
            )
            val id = repository.insert(screenshot)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Screenshot inserted to DB with ID: $id (Automatic Mode, marked for deletion)"
            )

            // Launch individual deletion timer
            launchDeletionTimer(id, deletionTime)

            NotificationHelper.showScreenshotNotification(
                this,
                id,
                fileName,
                deletionTimestamp,
                deletionTime,
                isManualMode = false
            )
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Notification shown for screenshot ID: $id"
            )

            // Notify UI to refresh
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Emitting refreshFlow after new screenshot"
            )
            refreshFlow.tryEmit(Unit)
        }
    }

    private fun launchDeletionTimer(screenshotId: Long, delayMillis: Long) {
        // Cancel any existing timer for this screenshot
        deletionJobs[screenshotId]?.cancel()

        val job = serviceScope.launch {
            try {
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Starting deletion timer for screenshot $screenshotId, delay: ${delayMillis}ms"
                )
                delay(delayMillis)
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Deletion timer expired for screenshot $screenshotId, about to delete"
                )

                // Check if screenshot still exists and is marked for deletion
                val screenshot = repository.getById(screenshotId)
                if (screenshot != null && screenshot.deletionTimestamp != null && !screenshot.isKept) {
                    DebugLogger.info(
                        "ScreenshotMonitorService",
                        "Deleting expired screenshot $screenshotId"
                    )
                    deleteExpiredScreenshot(screenshot)
                } else {
                    DebugLogger.info(
                        "ScreenshotMonitorService",
                        "Screenshot $screenshotId not found or not marked for deletion"
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    DebugLogger.error(
                        "ScreenshotMonitorService",
                        "Error in deletion timer for $screenshotId",
                        e
                    )
                }
            } finally {
                deletionJobs.remove(screenshotId)
            }
        }

        deletionJobs[screenshotId] = job
    }

    private fun cancelDeletionTimer(screenshotId: Long) {
        deletionJobs[screenshotId]?.cancel()
        deletionJobs.remove(screenshotId)
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Cancelled deletion timer for screenshot $screenshotId"
        )
    }

    private fun scanExistingScreenshots() {
        serviceScope.launch {
            try {
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Starting scan of existing screenshots"
                )

                val configuredUri = preferences.screenshotFolderUri.first()
                val screenshotFolder = if (configuredUri.isNotEmpty()) {
                    // Decode URI to path
                    java.net.URLDecoder.decode(configuredUri, "UTF-8").let { decoded ->
                        when {
                            decoded.contains("primary:") -> Environment.getExternalStorageDirectory().absolutePath + "/" + decoded.substringAfter(
                                "primary:"
                            )

                            decoded.contains("tree/") -> {
                                val parts = decoded.substringAfter("tree/").split(":")
                                if (parts.size >= 2) {
                                    val path = parts[1]
                                    Environment.getExternalStorageDirectory().absolutePath + "/" + path
                                } else decoded
                            }

                            else -> decoded
                        }
                    }
                } else {
                    // Default
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
                }

                DebugLogger.info("ScreenshotMonitorService", "Scanning folder: $screenshotFolder")
                val folder = File(screenshotFolder)
                if (!folder.exists() || !folder.isDirectory) {
                    DebugLogger.warning(
                        "ScreenshotMonitorService",
                        "Screenshot folder doesn't exist: $screenshotFolder, exists=${'$'}{folder.exists()}, isDir=${'$'}{folder.isDirectory}"
                    )
                    return@launch
                }

                val imageFiles = folder.listFiles { file ->
                    file.isFile && (file.extension.lowercase() in listOf("png", "jpg", "jpeg"))
                }

                val count = imageFiles?.size ?: 0
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Found $count existing screenshot files"
                )

                val screenshotsToImport = imageFiles?.mapNotNull { file ->
                if (file.exists() && file.length() > 0) {
                // Only import if not already in database
                val existing = repository.getByFilePath(file.absolutePath)
                if (existing == null) {
                Screenshot(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    createdAt = file.lastModified(),
                        deletionTimestamp = null,
                            isKept = false,
                                contentUri = null
                            )
                        } else null
                    } else null
                } ?: emptyList()

                // Also launch timers for any existing marked screenshots
                val existingMarked = repository.getMarkedScreenshotsLiveData()
                // Note: This is synchronous for simplicity, in real app should be async
                val markedList = existingMarked.value ?: emptyList()
                markedList.forEach { screenshot ->
                    val remainingTime =
                        screenshot.deletionTimestamp?.minus(System.currentTimeMillis()) ?: 0
                    if (remainingTime > 0) {
                        launchDeletionTimer(screenshot.id, remainingTime)
                    } else {
                        // Already expired, delete immediately
                        serviceScope.launch {
                            deleteExpiredScreenshot(screenshot)
                        }
                    }
                }

                val imported = if (screenshotsToImport.isNotEmpty()) {
                    screenshotsToImport.chunked(500).sumOf { chunk ->
                        repository.insertAll(chunk).count { it > 0 }
                    }
                } else 0

                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Imported $imported new screenshots from existing files"
                )
            } catch (e: Exception) {
                DebugLogger.error(
                    "ScreenshotMonitorService",
                    "Error scanning existing screenshots",
                    e
                )
            }
        }
    }

    private fun startDeletionCheckTimer() {
        deletionCheckJob = serviceScope.launch {
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val expiredScreenshots = repository.getExpiredScreenshots(currentTime)

                    if (expiredScreenshots.isNotEmpty()) {
                        DebugLogger.info(
                            "ScreenshotMonitorService",
                            "Found ${expiredScreenshots.size} expired screenshots, processing deletion"
                        )

                        expiredScreenshots.forEach { screenshot ->
                            // Cancel any existing timer for this screenshot
                            cancelDeletionTimer(screenshot.id)
                            deleteExpiredScreenshot(screenshot)
                        }
                    }

                    // Also check for screenshots that are no longer marked for deletion
                    val allMarked = repository.getMarkedScreenshotsLiveData().value ?: emptyList()
                    val currentlyTracked = deletionJobs.keys
                    val stillMarked = allMarked.map { it.id }.toSet()

                    // Cancel timers for screenshots that are no longer marked (kept or unmarked)
                    (currentlyTracked - stillMarked).forEach { screenshotId ->
                        DebugLogger.info(
                            "ScreenshotMonitorService",
                            "Cancelling timer for screenshot $screenshotId - no longer marked for deletion"
                        )
                        cancelDeletionTimer(screenshotId)
                    }

                    delay(5000L) // Check every 5 seconds for maintenance

                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        DebugLogger.error(
                            "ScreenshotMonitorService",
                            "Error checking expired screenshots",
                            e
                        )
                    }
                }
            }
        }
        DebugLogger.info("ScreenshotMonitorService", "Deletion check timer started")
    }

    private suspend fun deleteExpiredScreenshot(screenshot: Screenshot) {
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Deleting expired screenshot: ${screenshot.fileName}"
        )

        var deleted = false

        // Prefer deleting via contentUri when available
        screenshot.contentUri?.let { uriStr ->
            try {
                val uri = uriStr.toUri()
                val rows = contentResolver.delete(uri, null, null)
                deleted = rows > 0
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "ContentResolver delete result: $rows rows for ${screenshot.fileName}"
                )
            } catch (e: Exception) {
                DebugLogger.warning(
                    "ScreenshotMonitorService",
                    "ContentResolver delete failed for ${screenshot.id}: ${e.message}"
                )
            }
        }

        if (!deleted) {
            val file = File(screenshot.filePath)
            if (file.exists()) {
                deleted = file.delete()
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "File.delete() result: $deleted for ${screenshot.filePath}"
                )
            } else {
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "File doesn't exist, considering deleted: ${screenshot.filePath}"
                )
            }
        }

        // Always remove from database to prevent stuck state
        repository.delete(screenshot)
        NotificationHelper.cancelNotification(this, screenshot.id.toInt())
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Removed expired screenshot from database: ${screenshot.fileName}"
        )

        // Notify UI to refresh
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Emitting refreshFlow after deleting screenshot"
        )
        refreshFlow.tryEmit(Unit)
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.info("ScreenshotMonitorService", "Service onDestroy() called")

        // Cancel all deletion timers
        deletionJobs.values.forEach { it.cancel() }
        deletionJobs.clear()

        // Clear notification deduplication cache
        recentNotifications.clear()

        if (::contentObserver.isInitialized) {
            contentResolver.unregisterContentObserver(contentObserver)
        }
        deletionCheckJob?.cancel()
        serviceJob.cancel()
        serviceScope.launch {
            delay(CLEANUP_DELAY_MS)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
