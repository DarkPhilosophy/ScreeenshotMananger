package com.ko.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ko.app.ScreenshotApp
import com.ko.app.util.NotificationHelper
import com.ko.app.util.WorkManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
        if (screenshotId == -1L) return

        when (intent.action) {
            ACTION_KEEP -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as ScreenshotApp
                        app.repository.markAsKept(screenshotId)
                        NotificationHelper.cancelNotification(context, screenshotId.toInt())

                        // Emit refresh signal for UI update
                        app.emitRefresh()
                    } catch (_: Exception) {
                        // ignore errors in broadcast handling
                    }
                }
            }

            ACTION_DELETE -> {
                val isManualMode = intent.getBooleanExtra(EXTRA_IS_MANUAL_MODE, false)
                val deletionTimeMillis = intent.getLongExtra(EXTRA_DELETION_TIME_MILLIS, 60_000L)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as ScreenshotApp

                        if (isManualMode) {
                            // Manual mode: schedule deletion instead of immediate delete
                            val deletionTimestamp = System.currentTimeMillis() + deletionTimeMillis
                            app.repository.markForDeletion(screenshotId, deletionTimestamp)

                            // Schedule deletion work
                            WorkManagerScheduler.scheduleIndividualDeletion(
                                context,
                                screenshotId,
                                deletionTimeMillis
                            )
                        } else {
                            // Automatic mode: immediate delete
                            val screenshot = app.repository.getById(screenshotId)
                            screenshot?.let {
                                app.repository.delete(it)
                            }
                        }

                        NotificationHelper.cancelNotification(context, screenshotId.toInt())

                        // Emit refresh signal for UI update
                        app.emitRefresh()
                    } catch (_: Exception) {
                        // ignore errors in broadcast handling
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_KEEP = "com.ko.app.ACTION_KEEP"
        const val ACTION_DELETE = "com.ko.app.ACTION_DELETE"
        const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        const val EXTRA_IS_MANUAL_MODE = "is_manual_mode"
        const val EXTRA_DELETION_TIME_MILLIS = "deletion_time_millis"
    }
}
