package com.araara.screenapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.araara.screenapp.di.ReceiverEntryPoint
import com.araara.screenapp.service.ScreenshotMonitorService
import com.araara.screenapp.util.PermissionUtils
import com.araara.screenapp.util.WorkManagerScheduler
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
