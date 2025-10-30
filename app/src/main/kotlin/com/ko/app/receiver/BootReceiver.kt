package com.ko.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ko.app.ScreenshotApp
import com.ko.app.service.ScreenshotMonitorService
import com.ko.app.util.WorkManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as ScreenshotApp

            CoroutineScope(Dispatchers.IO).launch {
                val isServiceEnabled = app.preferences.serviceEnabled.first()

                if (isServiceEnabled) {
                    val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    WorkManagerScheduler.scheduleDeletionWork(context)
                }
            }
        }
    }
}

