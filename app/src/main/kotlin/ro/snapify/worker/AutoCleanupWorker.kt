package ro.snapify.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.snapify.data.repository.MediaRepository
import ro.snapify.util.DebugLogger
import ro.snapify.util.NotificationHelper
import java.io.File

@HiltWorker
class AutoCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MediaRepository
) : CoroutineWorker(context, workerParams) {

    private suspend fun performCleanup(): Int {
        val expiredMediaItems = repository.getExpiredMediaItems(System.currentTimeMillis())
        var deletedCount = 0
        for (mediaItem in expiredMediaItems) {
            val file = File(mediaItem.filePath)
            if (file.exists() && file.delete()) {
                repository.delete(mediaItem)
                deletedCount++
            } else {
                DebugLogger.error(
                    "AutoCleanupWorker",
                    "Failed to delete file: ${mediaItem.filePath}"
                )
            }
        }
        return deletedCount
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val deletedCount = performCleanup()
                DebugLogger.info(
                    "AutoCleanupWorker",
                    "Cleaned up $deletedCount expired screenshots"
                )
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
