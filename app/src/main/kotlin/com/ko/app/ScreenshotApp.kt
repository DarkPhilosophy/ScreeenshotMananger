package com.ko.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import com.ko.app.data.database.ScreenshotDatabase
import com.ko.app.data.preferences.AppPreferences
import com.ko.app.data.repository.ScreenshotRepository
import com.ko.app.util.DebugLogger
import kotlinx.coroutines.runBlocking
import java.util.Locale

class ScreenshotApp : Application() {

    lateinit var database: ScreenshotDatabase
        private set

    lateinit var repository: ScreenshotRepository
        private set

    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
    super.onCreate()
    instance = this

    database = ScreenshotDatabase.getDatabase(this)
        repository = ScreenshotRepository(database.screenshotDao())
    preferences = AppPreferences(this)

    val lang = runBlocking { preferences.getLanguageSync() }
        setLocale(lang)

        DebugLogger.init(this)

        createNotificationChannels()
        }

    private fun setLocale(language: String) {
        val locale = when (language) {
            "ro" -> Locale("ro", "RO")
            else -> Locale("en", "US")
        }
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Screenshot Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors screenshots in the background"
                setShowBadge(false)
            }

            val screenshotChannel = NotificationChannel(
                CHANNEL_ID_SCREENSHOT,
                "Screenshot Deletion Timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows countdown timers for screenshot deletion"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(screenshotChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_SERVICE = "screenshot_monitor_service"
        const val CHANNEL_ID_SCREENSHOT = "screenshot_deletion"

        lateinit var instance: ScreenshotApp
            private set
    }
}
