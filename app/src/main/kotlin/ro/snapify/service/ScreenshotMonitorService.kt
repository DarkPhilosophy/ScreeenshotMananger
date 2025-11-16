package ro.snapify.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ro.snapify.ScreenshotApp
import ro.snapify.data.entity.MediaItem
import ro.snapify.ui.MainActivity
import ro.snapify.ui.MainViewModel
import ro.snapify.ui.RefreshReason
import ro.snapify.util.DebugLogger
import ro.snapify.util.NotificationHelper
import ro.snapify.util.PermissionUtils
import java.io.File
import javax.inject.Inject

private const val MILLIS_PER_SECOND = 1000L
private const val PROCESSING_DELAY_MS = 500L
private const val CLEANUP_DELAY_MS = 1000L

@AndroidEntryPoint
class ScreenshotMonitorService : Service() {

    private fun performInitialSetup() {
        setupContentObserver()
        startDeletionCheckTimer()

        serviceScope.launch {
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Scanning existing media on service start"
            )
            scanExistingMedia()
            observeConfiguredFolders()
            cleanUpExpiredMediaItems()
            cleanUpMissingMediaItems()
        }
    }

    private suspend fun cleanUpExpiredMediaItems() {
        val currentTime = System.currentTimeMillis()
        val expired = repository.getExpiredMediaItems(currentTime)
        expired.filter { mediaItem ->
            mediaItem.contentUri?.let { uriStr ->
                try {
                    val uri = uriStr.toUri()
                    val cursor = contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Images.Media._ID),
                        null,
                        null,
                        null
                    )
                    cursor?.use { it.count > 0 } ?: !File(mediaItem.filePath).exists()
                } catch (_: Exception) {
                    !File(mediaItem.filePath).exists()
                }
            } ?: !File(mediaItem.filePath).exists()
        }.forEach { mediaItem ->
            repository.delete(mediaItem)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Cleaned up expired media item: ${mediaItem.fileName}"
            )
        }
    }

    private suspend fun cleanUpMissingMediaItems() {
        val allItems = repository.getAllMediaItems().first()
        allItems.filter { mediaItem ->
            !File(mediaItem.filePath).exists()
        }.forEach { mediaItem ->
            repository.delete(mediaItem)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Cleaned up missing media item: ${mediaItem.fileName}"
            )
        }
    }

    @Inject
    lateinit var repository: ro.snapify.data.repository.MediaRepository

    @Inject
    lateinit var refreshFlow: MutableSharedFlow<RefreshReason>

    @Inject
    lateinit var preferences: ro.snapify.data.preferences.AppPreferences

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var contentObserver: ContentObserver
    private var deletionCheckJob: Job? = null

    // Individual deletion timers for each marked screenshot
    private val deletionJobs = mutableMapOf<Long, Job>()
    private val updateJobs = mutableMapOf<Long, Job>()

    // Deduplication for screenshot notifications - track recent notifications
    private val recentNotifications = mutableMapOf<String, Long>() // filePath -> timestamp
    private val NOTIFICATION_DEDUPE_WINDOW = 5000L // 5 seconds window

    override fun onCreate() {
        super.onCreate()

        DebugLogger.info("ScreenshotMonitorService", "Service onCreate() called")

        // For foreground service, we must call startForeground within a few seconds
        // So call it immediately, even if we'll stop due to missing permissions
        startForeground(
            NOTIFICATION_ID,
            createForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // Check permissions after starting foreground to avoid system timeout
        if (PermissionUtils.getMissingPermissions(this).isNotEmpty()) {
            DebugLogger.error(
                "ScreenshotMonitorService",
                "Required permissions not granted, stopping service"
            )
            stopSelf()
            return
        }

        performInitialSetup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.info("ScreenshotMonitorService", "onStartCommand called")

        if (intent?.getBooleanExtra("rescan", false) == true) {
            serviceScope.launch {
                DebugLogger.info("ScreenshotMonitorService", "Rescanning triggered from intent")
                scanExistingMedia()
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
                uri?.let { handleNewMedia(it) }
            }
        }

        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.registerContentObserver(imageUri, true, contentObserver)
        contentResolver.registerContentObserver(videoUri, true, contentObserver)
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Content observers registered for $imageUri and $videoUri"
        )

        // Perform initial scan of existing media
        scanExistingMedia()
    }

    private fun scanExistingMedia() {
        serviceScope.launch {
            try {
                DebugLogger.info("ScreenshotMonitorService", "Starting initial media scan")

                // Preload existing file paths to avoid per-file DB queries
                val existingFilePaths =
                    repository.getAllMediaItems().first().map { it.filePath }.toSet()

                var totalInserted = 0

                // Scan MediaStore for existing media (relies on MediaStore being properly indexed)
                // Scan images
                totalInserted += scanMediaType(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    false,
                    existingFilePaths
                )

                // Scan videos
                totalInserted += scanMediaType(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    existingFilePaths
                )

                // Emit refresh only if new media was discovered
                if (totalInserted > 0) {
                    refreshFlow.emit(RefreshReason.Other)
                }

                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Initial media scan completed, inserted $totalInserted new items"
                )
            } catch (e: Exception) {
                DebugLogger.error("ScreenshotMonitorService", "Error during initial media scan", e)
            }
        }
    }

    private suspend fun scanMediaType(
        uri: Uri,
        isVideo: Boolean,
        existingFilePaths: Set<String>
    ): Int {
        var insertedCount = 0
        // Get configured media folders
        val configuredUris = preferences.mediaFolderUris.first()
        val mediaFolders = if (configuredUris.isEmpty()) {
            listOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots")
        } else {
            configuredUris.map { uri ->
                if (uri.isEmpty()) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
                } else {
                    try {
                        val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
                        when {
                            decoded.contains("primary:") -> Environment.getExternalStorageDirectory().absolutePath + "/" + decoded.substringAfter(
                                "primary:"
                            ).replace(":", "/")

                            decoded.contains("tree/") -> {
                                val parts = decoded.substringAfter("tree/").split(":")
                                if (parts.size >= 2) Environment.getExternalStorageDirectory().absolutePath + "/" + parts.drop(
                                    1
                                ).joinToString("/") else decoded
                            }

                            else -> decoded
                        }
                    } catch (_: Exception) {
                        uri
                    }
                }
            }
        }

        // Query all media (no date limit to ensure all folder contents are scanned, even old files)

        val projection = if (isVideo) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
            )
        }

        contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id =
                    cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Images.Media._ID))
                val contentUri = ContentUris.withAppendedId(uri, id).toString()
                val fileName =
                    cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME))
                val fileSize =
                    cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE))
                val dateAdded =
                    cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATE_ADDED else MediaStore.Video.Media.DATE_ADDED))
                val filePath =
                    cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATA else MediaStore.Images.Media.DATA))

                // Create a fake Uri for processing
                val fakeUri = Uri.parse(contentUri)

                // Check if this media should be processed
                if (isMediaFile(filePath ?: "")) {
                    val wasInserted = filePath != null && filePath !in existingFilePaths
                    if (wasInserted) insertedCount++
                    processExistingMedia(
                        fakeUri,
                        contentUri,
                        filePath,
                        fileName,
                        fileSize,
                        dateAdded * 1000,
                        existingFilePaths
                    )
                }
            }
        }
        return insertedCount
    }

    private suspend fun processExistingMedia(
        uri: Uri,
        contentUri: String,
        filePath: String?,
        fileName: String,
        fileSize: Long,
        createdAt: Long,
        existingFilePaths: Set<String>
    ) {
        try {
            // Check if already exists in database (using preloaded set for performance)
            if (filePath != null && filePath in existingFilePaths) {
                DebugLogger.debug("ScreenshotMonitorService", "Media already exists: $filePath")
                return
            }

            // Validate existence
            val exists = contentUri.let { uriStr ->
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

            if (exists) {
                DebugLogger.info("ScreenshotMonitorService", "Processing existing media: $fileName")

                val mediaItem = MediaItem(
                    filePath = filePath ?: "",
                    fileName = fileName,
                    fileSize = fileSize,
                    createdAt = createdAt,
                    deletionTimestamp = null,
                    isKept = false,
                    contentUri = contentUri
                )

                repository.insert(mediaItem)
                // Don't emit refresh for each item - will emit once at the end

                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "Added existing media to database: $fileName"
                )
            }
        } catch (e: Exception) {
            DebugLogger.error(
                "ScreenshotMonitorService",
                "Error processing existing media $fileName",
                e
            )
        }
    }

    private fun handleNewMedia(uri: Uri) {
        val isVideo = uri.toString().contains("video")
        val projection = if (isVideo) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
            )
        }
        serviceScope.launch {
            try {
                DebugLogger.debug("ScreenshotMonitorService", "New media detected: $uri")
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val mediaClass =
                            if (isVideo) MediaStore.Video.Media::class.java else MediaStore.Images.Media::class.java
                        val idIndex =
                            cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Images.Media._ID)
                        val fileName =
                            cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME))
                        val sizeIndex =
                            cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE)
                        val dateIndex =
                            cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATE_ADDED else MediaStore.Images.Media.DATE_ADDED)
                        val dataIndex =
                            cursor.getColumnIndex(if (isVideo) MediaStore.Video.Media.DATA else MediaStore.Images.Media.DATA)

                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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

                        val isMedia = filePath != null && isMediaFile(filePath)

                        if (isMedia) {
                            DebugLogger.info(
                                "ScreenshotMonitorService",
                                "Media file detected: $fileName"
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

    private fun observeConfiguredFolders() {
        serviceScope.launch {
            preferences.mediaFolderUris.collect { uris ->
                DebugLogger.info("ScreenshotMonitorService", "Configured folders changed: $uris")
                scanExistingMedia()
                refreshFlow.emit(RefreshReason.Other)
            }
        }
    }

    private suspend fun isMediaFile(filePath: String): Boolean {
        if (filePath.isEmpty()) return false

        val lowerPath = filePath.lowercase()

        // Get configured media folders (same logic as in scanMediaType)
        val configuredUris = preferences.mediaFolderUris.first()
        val mediaFolders = if (configuredUris.isEmpty()) {
            listOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots")
        } else {
            configuredUris.map { uri ->
                if (uri.isEmpty()) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
                } else {
                    try {
                        val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
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
                        uri
                    }
                }
            }
        }

        val isInFolder = mediaFolders.any { folder -> lowerPath.contains(folder.lowercase()) }
        val isVideo =
            lowerPath.endsWith(".mp4") || lowerPath.endsWith(".avi") || lowerPath.endsWith(".mov") ||
                    lowerPath.endsWith(".mkv") || lowerPath.endsWith(".webm") || lowerPath.endsWith(
                ".3gp"
            ) ||
                    lowerPath.endsWith(".m4v") || lowerPath.endsWith(".mpg")
        val isImage =
            lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                    lowerPath.endsWith(".gif") || lowerPath.endsWith(".bmp") || lowerPath.endsWith(".webp")

        // Include media files that are in configured folders
        return isInFolder && (isVideo || isImage)
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
            val mediaItem = MediaItem(
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
                "Attempting to insert media item: ${mediaItem.fileName}"
            )
            val id = repository.insert(mediaItem)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Media item inserted to DB with ID: $id (Manual Mode)"
            )
            if (id <= 0) {
                DebugLogger.error(
                    "ScreenshotMonitorService",
                    "Failed to insert media item, invalid ID: $id"
                )
                return
            }

            // Notify UI of new item
            MainViewModel.newItemFlow.tryEmit(mediaItem.copy(id = id))
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Emitted new item to UI: ${mediaItem.fileName}"
            )

            // Show overlay for manual mode
            val overlayIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("screenshot_id", id)
                putExtra("file_path", filePath)
            }
            startService(overlayIntent)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Overlay shown for manual mode media item ID: $id"
            )
        } else {
            val deletionTime = preferences.deletionTimeMillis.first()
            val deletionTimestamp = System.currentTimeMillis() + deletionTime

            val mediaItem = MediaItem(
                filePath = filePath ?: "",
                fileName = fileName,
                fileSize = actualFileSize,
                createdAt = createdAt,
                deletionTimestamp = deletionTimestamp,
                isKept = false,
                contentUri = contentUri
            )
            val id = repository.insert(mediaItem)
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Media item inserted to DB with ID: $id (Automatic Mode, marked for deletion)"
            )

            // Notify UI of new item
            MainViewModel.newItemFlow.tryEmit(mediaItem.copy(id = id))
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Emitted new item to UI: ${mediaItem.fileName}"
            )

            // Launch individual deletion timer
            launchDeletionTimer(id, deletionTime)

            val currentTime = System.currentTimeMillis()
            val calculatedDeletionTimestamp = currentTime + deletionTime

            NotificationHelper.showScreenshotNotification(
                this,
                id,
                fileName,
                mediaItem.filePath,
                calculatedDeletionTimestamp,
                deletionTime,
                isManualMode = false,
                preferences = preferences
            )
            DebugLogger.info(
                "ScreenshotMonitorService",
                "Notification shown for media item ID: $id"
            )

            // Launch notification update job for live updates
            val updateJob = serviceScope.launch {
                while (deletionJobs[id]?.isActive == true) {
                    delay(1000L) // Update every 1 second
                    try {
                        NotificationHelper.showScreenshotNotification(
                            this@ScreenshotMonitorService,
                            id,
                            fileName,
                            mediaItem.filePath,
                            calculatedDeletionTimestamp,
                            deletionTime,
                            isManualMode = false,
                            preferences = preferences
                        )
                    } catch (e: Exception) {
                        DebugLogger.error(
                            "ScreenshotMonitorService",
                            "Error updating notification for $id",
                            e
                        )
                        // Continue the loop even if one update fails
                    }
                }
            }
            updateJobs[id] = updateJob

            // UI is already notified via newItemFlow - no need for full refresh
            // refreshFlow.tryEmit(RefreshReason.Other)
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

                // Check if media item still exists and is marked for deletion
                val mediaItem = repository.getById(screenshotId)
                if (mediaItem != null && mediaItem.deletionTimestamp != null && !mediaItem.isKept) {
                    DebugLogger.info(
                        "ScreenshotMonitorService",
                        "Deleting expired media item $screenshotId"
                    )
                    deleteExpiredMediaItem(mediaItem)
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
                updateJobs[screenshotId]?.cancel()
                updateJobs.remove(screenshotId)
            }
        }

        deletionJobs[screenshotId] = job
    }

    private fun cancelDeletionTimer(screenshotId: Long) {
        deletionJobs[screenshotId]?.cancel()
        deletionJobs.remove(screenshotId)
        updateJobs[screenshotId]?.cancel()
        updateJobs.remove(screenshotId)
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Cancelled deletion timer for screenshot $screenshotId"
        )
    }


    private fun startDeletionCheckTimer() {
        deletionCheckJob = serviceScope.launch {
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val expiredMediaItems = repository.getExpiredMediaItems(currentTime)

                    if (expiredMediaItems.isNotEmpty()) {
                        DebugLogger.info(
                            "ScreenshotMonitorService",
                            "Found ${expiredMediaItems.size} expired media items, processing deletion"
                        )

                        expiredMediaItems.forEach { mediaItem ->
                            // Cancel any existing timer for this media item
                            cancelDeletionTimer(mediaItem.id)
                            deleteExpiredMediaItem(mediaItem)
                        }
                    }

                    // Also check for media items that are no longer marked for deletion
                    val allMarked = repository.getMarkedMediaItems().first()
                    val currentlyTracked = deletionJobs.keys
                    val stillMarked = allMarked.map { it.id }.toSet()

                    // Cancel timers for media items that are no longer marked (kept or unmarked)
                    (currentlyTracked - stillMarked).forEach { mediaItemId ->
                        DebugLogger.info(
                            "ScreenshotMonitorService",
                            "Cancelling timer for media item $mediaItemId - no longer marked for deletion"
                        )
                        cancelDeletionTimer(mediaItemId)
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

    private suspend fun deleteExpiredMediaItem(mediaItem: MediaItem) {
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Deleting expired media item: ${mediaItem.fileName}"
        )

        var deleted = false

        // Prefer deleting via contentUri when available
        mediaItem.contentUri?.let { uriStr ->
            try {
                val uri = uriStr.toUri()
                val rows = contentResolver.delete(uri, null, null)
                deleted = rows > 0
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "ContentResolver delete result: $rows rows for ${mediaItem.fileName}"
                )
            } catch (e: Exception) {
                DebugLogger.warning(
                    "ScreenshotMonitorService",
                    "ContentResolver delete failed for ${mediaItem.id}: ${e.message}"
                )
            }
        }

        if (!deleted) {
            val file = File(mediaItem.filePath)
            if (file.exists()) {
                deleted = file.delete()
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "File.delete() result: $deleted for ${mediaItem.filePath}"
                )
            } else {
                DebugLogger.info(
                    "ScreenshotMonitorService",
                    "File doesn't exist, considering deleted: ${mediaItem.filePath}"
                )
            }
        }

        // Always remove from database to prevent stuck state
        repository.delete(mediaItem)
        NotificationHelper.cancelNotification(this, mediaItem.id.toInt())
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Removed expired media item from database: ${mediaItem.fileName}"
        )

        // Notify UI to remove item
        DebugLogger.info(
            "ScreenshotMonitorService",
            "Emitting deleteItemFlow for ${mediaItem.id}"
        )
        MainViewModel.deleteItemFlow.tryEmit(mediaItem.id)
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.info("ScreenshotMonitorService", "Service onDestroy() called")

        // Cancel all deletion timers
        deletionJobs.values.forEach { it.cancel() }
        deletionJobs.clear()
        updateJobs.values.forEach { it.cancel() }
        updateJobs.clear()

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
