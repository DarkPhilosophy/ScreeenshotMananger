package com.araara.screenapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.araara.screenapp.data.preferences.AppPreferences
import com.araara.screenapp.data.repository.ScreenshotRepository
import com.araara.screenapp.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScreenshotApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var repository: ScreenshotRepository

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var refreshFlow: MutableSharedFlow<Unit>

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize language manager synchronously to avoid delays
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val lang = preferences.getLanguageSync()
                LanguageManager.initialize(lang)
                val localeTag = if (lang == "ro") "ro" else "en"
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
            } catch (e: Exception) {
                // If anything fails, default to English
                LanguageManager.initialize("en")
                DebugLogger.warning("ScreenshotApp", "Failed to initialize language", e)
            }
        }

        // Listen for language changes and update cache
        CoroutineScope(Dispatchers.Default).launch {
            preferences.language.collect { newLanguage ->
                LanguageManager.updateLanguage(newLanguage)
                val localeTag = if (newLanguage == "ro") "ro" else "en"
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
            }
        }

        DebugLogger.init(this)

        createNotificationChannels()
    }

    fun emitRefresh() {
        refreshFlow.tryEmit(Unit)
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

    // Replace getWorkManagerConfiguration() method with WorkManager's property API
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        const val CHANNEL_ID_SERVICE = "screenshot_monitor_service"
        const val CHANNEL_ID_SCREENSHOT = "screenshot_deletion"

        lateinit var instance: ScreenshotApp
            private set
    }
}

/**
 * Singleton language manager that caches the current language to avoid disk I/O on every activity creation.
 */
object LanguageManager {
    private var currentLanguage: String = "en" // Default fallback
    private var isInitialized = false

    fun initialize(language: String) {
        currentLanguage = language
        isInitialized = true
    }

    fun updateLanguage(language: String) {
        currentLanguage = language
    }

    fun getCurrentLanguage(): String {
        return currentLanguage
    }
}
