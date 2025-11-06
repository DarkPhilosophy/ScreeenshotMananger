package com.ko.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ko.app.di.ReceiverEntryPoint
import com.ko.app.service.ScreenshotMonitorService
import com.ko.app.util.PermissionUtils
import com.ko.app.util.WorkManagerScheduler
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ReceiverEntryPoint::class.java
            )
            val preferences = entryPoint.preferences()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val isServiceEnabled = preferences.serviceEnabled.first()

                    if (isServiceEnabled && PermissionUtils.getMissingPermissions(context)
                            .isEmpty()
                    ) {
                        val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }

                        WorkManagerScheduler.scheduleDeletionWork(context)
                    }
                } catch (_: Exception) {
                    // ignore boot handling errors
                }
            }
        }
    }
}
