package com.ko.app.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ScreenshotDeletionWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: com.ko.app.data.repository.ScreenshotRepository,
    private val refreshFlow: kotlinx.coroutines.flow.MutableSharedFlow<Unit>
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val screenshotId = inputData.getLong("screenshot_id", -1L)

        return if (screenshotId != -1L) {
            // Individual deletion request
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "Worker started for individual screenshot ID: $screenshotId"
            )
            try {
                val screenshot = repository.getById(screenshotId)
                if (screenshot != null) {
                    deleteScreenshot(screenshot)
                } else {
                    DebugLogger.warning(
                        "ScreenshotDeletionWorker",
                        "Screenshot not found for ID: $screenshotId"
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
                val expiredScreenshots = repository.getExpiredScreenshots(currentTime)

                DebugLogger.info(
                    "ScreenshotDeletionWorker",
                    "Found ${expiredScreenshots.size} expired screenshots"
                )

                expiredScreenshots.forEach { screenshot ->
                    deleteScreenshot(screenshot)
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

    private suspend fun deleteScreenshot(screenshot: com.ko.app.data.entity.Screenshot) {
        DebugLogger.info(
            "ScreenshotDeletionWorker",
            "Attempting to delete screenshot ID: ${screenshot.id}, path: ${screenshot.filePath}, contentUri: ${screenshot.contentUri}"
        )
        var deleted = false

        // Prefer deleting via contentUri when available
        screenshot.contentUri?.let { uriStr ->
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
                    "ContentResolver delete failed for ${screenshot.id}: ${e.message}",
                    e
                )
            }
        }

        if (!deleted) {
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "ContentResolver failed or not available, trying File.delete for ${screenshot.filePath}"
            )
            val file = File(screenshot.filePath)
            val exists = file.exists()
            val canRead = file.canRead()
            val canWrite = file.canWrite()
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "File ${screenshot.filePath} - exists: $exists, canRead: $canRead, canWrite: $canWrite"
            )
            if (exists) {
                deleted = file.delete()
                DebugLogger.info(
                    "ScreenshotDeletionWorker",
                    "File.delete() result: $deleted for ${screenshot.filePath}"
                )
            } else {
                DebugLogger.warning(
                    "ScreenshotDeletionWorker",
                    "File does not exist: ${screenshot.filePath}, considering as deleted"
                )
                deleted = true // Consider deleted if not exists
            }
        }

        if (deleted) {
            repository.delete(screenshot)
            NotificationHelper.cancelNotification(applicationContext, screenshot.id.toInt())
            DebugLogger.info(
                "ScreenshotDeletionWorker",
                "Successfully deleted screenshot ID: ${screenshot.id}"
            )
        } else {
            DebugLogger.warning(
                "ScreenshotDeletionWorker",
                "Failed to delete file for screenshot ID: ${screenshot.id}, but removing from database"
            )
            // Still delete from database to prevent stuck state, even if file deletion failed
            // This could happen if file was already deleted externally or permission issues
            repository.delete(screenshot)
            NotificationHelper.cancelNotification(applicationContext, screenshot.id.toInt())
        }

        // Emit refresh to update UI
        refreshFlow.tryEmit(Unit)
    }
}
