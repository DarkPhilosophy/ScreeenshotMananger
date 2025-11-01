package com.ko.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ko.app.ScreenshotApp
import com.ko.app.util.NotificationHelper
import java.io.File

class ScreenshotDeletionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as ScreenshotApp
            val currentTime = System.currentTimeMillis()
            val expiredScreenshots = app.repository.getExpiredScreenshots(currentTime)

            expiredScreenshots.forEach { screenshot ->
                deleteScreenshot(app, screenshot)
            }

            Result.success()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            // Log the exception
            android.util.Log.e("ScreenshotDeletionWorker", "Work failed", e)
            Result.retry()
        }
    }

    private suspend fun deleteScreenshot(app: ScreenshotApp, screenshot: com.ko.app.data.entity.Screenshot) {
        val file = File(screenshot.filePath)
        val shouldDelete = !file.exists() || file.delete()

        if (shouldDelete) {
            app.repository.delete(screenshot)
            val notificationHelper = NotificationHelper(applicationContext)
            notificationHelper.cancelNotification(screenshot.id.toInt())
        }
    }
}
