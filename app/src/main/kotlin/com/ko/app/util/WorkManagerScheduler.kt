package com.ko.app.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ko.app.worker.ScreenshotDeletionWorker
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    private const val DELETION_WORK_NAME = "screenshot_deletion_work"

    fun scheduleDeletionWork(context: Context) {
        val deletionWorkRequest = PeriodicWorkRequestBuilder<ScreenshotDeletionWorker>(
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DELETION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            deletionWorkRequest
        )
    }

    fun cancelDeletionWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DELETION_WORK_NAME)
    }
}

