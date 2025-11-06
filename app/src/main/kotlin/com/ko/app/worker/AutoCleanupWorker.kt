package com.ko.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ko.app.data.repository.ScreenshotRepository
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class AutoCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScreenshotRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val expiredScreenshots = repository.getExpiredScreenshots(System.currentTimeMillis())
                var deletedCount = 0
                for (screenshot in expiredScreenshots) {
                    val file = File(screenshot.filePath)
                    if (file.exists() && file.delete()) {
                        repository.delete(screenshot)
                        deletedCount++
                    } else {
                        DebugLogger.error("AutoCleanupWorker", "Failed to delete file: ${screenshot.filePath}")
                    }
                }
                DebugLogger.info("AutoCleanupWorker", "Cleaned up $deletedCount expired screenshots")
                if (deletedCount > 0) {
                    NotificationHelper.showCleanupNotification(applicationContext, deletedCount)
                }
                Result.success()
            } catch (e: Exception) {
                DebugLogger.error("AutoCleanupWorker", "Cleanup failed", e)
                Result.retry()
            }
        }
    }
}
