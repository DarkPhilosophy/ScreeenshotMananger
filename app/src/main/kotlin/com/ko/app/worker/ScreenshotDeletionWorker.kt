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
                val file = File(screenshot.filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        app.repository.delete(screenshot)

                        val notificationHelper = NotificationHelper(applicationContext)
                        notificationHelper.cancelNotification(screenshot.id.toInt())
                    }
                } else {
                    app.repository.delete(screenshot)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

