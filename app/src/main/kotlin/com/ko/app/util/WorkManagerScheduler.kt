package com.ko.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ko.app.worker.ScreenshotDeletionWorker
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    fun scheduleDeletionWork(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val deletionWork = PeriodicWorkRequestBuilder<ScreenshotDeletionWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "screenshot_deletion",
            ExistingPeriodicWorkPolicy.REPLACE,
            deletionWork
        )
    }

    fun scheduleIndividualDeletion(context: Context, screenshotId: Long, delayMillis: Long) {
        val workManager = WorkManager.getInstance(context)

        val inputData = Data.Builder()
            .putLong("screenshot_id", screenshotId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val deletionWork = OneTimeWorkRequestBuilder<ScreenshotDeletionWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        val workName = "deletion_$screenshotId"
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            deletionWork
        )
    }
}
