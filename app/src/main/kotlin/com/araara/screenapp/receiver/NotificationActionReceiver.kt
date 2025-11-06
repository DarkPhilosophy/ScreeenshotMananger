package com.araara.screenapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.araara.screenapp.ScreenshotApp
import com.araara.screenapp.util.NotificationHelper
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
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as ScreenshotApp

                        // Always perform immediate deletion when "Delete Now" is pressed from notification
                        val screenshot = app.repository.getById(screenshotId)
                        screenshot?.let {
                            app.repository.delete(it)
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
        const val ACTION_KEEP = "com.araara.screenapp.ACTION_KEEP"
        const val ACTION_DELETE = "com.araara.screenapp.ACTION_DELETE"
        const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        const val EXTRA_IS_MANUAL_MODE = "is_manual_mode"
        const val EXTRA_DELETION_TIME_MILLIS = "deletion_time_millis"
    }
}
