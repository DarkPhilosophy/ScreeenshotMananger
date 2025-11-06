package com.ko.app.util

import android.content.Context
import android.os.Build
import android.provider.Settings

object PermissionUtils {
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_MEDIA_IMAGES
                )
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasStorageWritePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, check if we have MANAGE_EXTERNAL_STORAGE
            android.os.Environment.isExternalStorageManager()
        } else {
            // For Android 10 and below, check WRITE_EXTERNAL_STORAGE
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!hasStoragePermission(context)) missing.add("storage")
        if (!hasStorageWritePermission(context)) missing.add("storage_write")
        if (!hasNotificationPermission(context)) missing.add("notification")
        if (!hasOverlayPermission(context)) missing.add("overlay")
        return missing
    }
}

