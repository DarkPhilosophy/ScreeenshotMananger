package ro.snapify.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ro.snapify.data.repository.MediaRepository
import ro.snapify.util.DebugLogger
import ro.snapify.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ScreenshotDeletionWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: MediaRepository,
    private val refreshFlow: kotlinx.coroutines.flow.MutableSharedFlow<Unit>
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val mediaItemId = inputData.getLong("media_item_id", -1L)

        return if (mediaItemId != -1L) {
            // Individual deletion request
            DebugLogger.info(
            "ScreenshotDeletionWorker",
            "Worker started for individual media item ID: $mediaItemId"
            )
            try {
                val mediaItem = repository.getById(mediaItemId)
                if (mediaItem != null) {
                    deleteMediaItem(mediaItem)
                } else {
                    DebugLogger.warning(
                    "ScreenshotDeletionWorker",
                    "Media item not found for ID: $mediaItemId"
                    )
                }
                Result.success()
            } catch (_: SecurityException) {
                DebugLogger.error(
                    "ScreenshotDeletionWorker",
                    "SecurityException during individual deletion"
                )
                Result.failure()
            } catch (e: Exception) {
                DebugLogger.error(
                    "ScreenshotDeletionWorker",
                    "Individual deletion work failed: ${e.message}",
                    e
                )
                Result.retry()
            }
        } else {
            // Fallback cleanup for any missed deletions
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "Worker started (fallback for any missed deletions)"
            )
            try {
                val currentTime = System.currentTimeMillis()
                val expiredMediaItems = repository.getExpiredMediaItems(currentTime)

                DebugLogger.info(
                "ScreenshotDeletionWorker",
                "Found ${expiredMediaItems.size} expired media items"
                )

                expiredMediaItems.forEach { mediaItem ->
                    deleteMediaItem(mediaItem)
                }

                Result.success()
            } catch (_: SecurityException) {
                DebugLogger.error("ScreenshotDeletionWorker", "SecurityException during deletion")
                Result.failure()
            } catch (e: Exception) {
                DebugLogger.error("ScreenshotDeletionWorker", "Work failed: ${e.message}", e)
                Result.retry()
            }
        }
    }

    private suspend fun deleteMediaItem(mediaItem: ro.snapify.data.entity.MediaItem) {
        DebugLogger.info(
        "ScreenshotDeletionWorker",
        "Attempting to delete media item ID: ${mediaItem.id}, path: ${mediaItem.filePath}, contentUri: ${mediaItem.contentUri}"
        )
        var deleted = false

        // Prefer deleting via contentUri when available
        mediaItem.contentUri?.let { uriStr ->
            try {
                DebugLogger.info(
                    "ScreenshotDeletionWorker",
                    "Trying ContentResolver delete for URI: $uriStr"
                )
                val uri = uriStr.toUri()
                val rows = applicationContext.contentResolver.delete(uri, null, null)
                deleted = rows > 0
                DebugLogger.info(
                    "ScreenshotDeletionWorker",
                    "ContentResolver delete rows: $rows for URI: $uriStr, deleted: $deleted"
                )
            } catch (e: Exception) {
                DebugLogger.warning(
                "ScreenshotDeletionWorker",
                "ContentResolver delete failed for ${mediaItem.id}: ${e.message}",
                e
                )
            }
        }

        if (!deleted) {
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "ContentResolver failed or not available, trying File.delete for ${mediaItem.filePath}"
                )
                val file = File(mediaItem.filePath)
                val exists = file.exists()
                val canRead = file.canRead()
                val canWrite = file.canWrite()
                DebugLogger.info(
                "ScreenshotDeletionWorker",
                "File ${mediaItem.filePath} - exists: $exists, canRead: $canRead, canWrite: $canWrite"
                )
                if (exists) {
                deleted = file.delete()
                DebugLogger.info(
                "ScreenshotDeletionWorker",
                "File.delete() result: $deleted for ${mediaItem.filePath}"
                )
                } else {
                DebugLogger.warning(
                "ScreenshotDeletionWorker",
                "File does not exist: ${mediaItem.filePath}, considering as deleted"
                )
                deleted = true // Consider deleted if not exists
            }
        }

        if (deleted) {
        repository.delete(mediaItem)
        NotificationHelper.cancelNotification(applicationContext, mediaItem.id.toInt())
        DebugLogger.info(
        "ScreenshotDeletionWorker",
        "Successfully deleted media item ID: ${mediaItem.id}"
        )
        } else {
        DebugLogger.warning(
        "ScreenshotDeletionWorker",
        "Failed to delete file for media item ID: ${mediaItem.id}, but removing from database"
        )
        // Still delete from database to prevent stuck state, even if file deletion failed
        // This could happen if file was already deleted externally or permission issues
        repository.delete(mediaItem)
        NotificationHelper.cancelNotification(applicationContext, mediaItem.id.toInt())
        }

        // Emit refresh to update UI
        refreshFlow.tryEmit(Unit)
    }
}
