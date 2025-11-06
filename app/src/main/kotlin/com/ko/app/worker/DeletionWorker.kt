package com.ko.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ko.app.data.repository.ScreenshotRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DeletionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScreenshotRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val screenshotId = inputData.getLong("screenshot_id", -1L)
        if (screenshotId == -1L) return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val screenshot = repository.getById(screenshotId)
                screenshot?.let {
                    val file = File(it.filePath)
                    if (file.exists() && file.delete()) {
                        repository.deleteById(screenshotId)
                    } else {
                        // Failed to delete file, but remove from DB anyway?
                        repository.deleteById(screenshotId)
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
