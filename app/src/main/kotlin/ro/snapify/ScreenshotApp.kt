package ro.snapify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.data.repository.MediaRepository
import ro.snapify.ui.RefreshReason
import ro.snapify.util.DebugLogger
import javax.inject.Inject

@HiltAndroidApp
class ScreenshotApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var repository: MediaRepository

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var refreshFlow: MutableSharedFlow<RefreshReason>

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

        // Initialize WorkManager with HiltWorkerFactory
        WorkManager.initialize(this, workManagerConfiguration)

        createNotificationChannels()
    }

    fun emitRefresh() {
        refreshFlow.tryEmit(RefreshReason.Other)
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
