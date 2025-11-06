package com.araara.screenapp.ui

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.araara.screenapp.data.preferences.AppPreferences
import com.araara.screenapp.ui.theme.AppTheme
import com.araara.screenapp.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle language changes
        lifecycleScope.launch {
            preferences.language.collect { language ->
                val localeTag = if (language == "ro") "ro" else "en"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = getSystemService(LocaleManager::class.java)
                    localeManager?.applicationLocales = LocaleList.forLanguageTags(localeTag)
                } else {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(
                            localeTag
                        )
                    )
                }
            }
        }

        // Handle auto cleanup worker
        lifecycleScope.launch {
            preferences.autoCleanupEnabled.collect { enabled ->
                val workManager = WorkManager.getInstance(this@MainActivity)
                if (enabled) {
                    val constraints = Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                    val request =
                        PeriodicWorkRequestBuilder<com.araara.screenapp.worker.AutoCleanupWorker>(
                            24,
                            java.util.concurrent.TimeUnit.HOURS
                        )
                            .setConstraints(constraints)
                            .build()
                    workManager.enqueueUniquePeriodicWork(
                        "auto_cleanup",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
                } else {
                    workManager.cancelUniqueWork("auto_cleanup")
                }
            }
        }

        setContent {
            val themeModeString by preferences.themeMode.collectAsState(initial = "system")
            val themeMode = when (themeModeString) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "dynamic" -> ThemeMode.DYNAMIC
                "oled" -> ThemeMode.OLED
                else -> ThemeMode.SYSTEM
            }

            AppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(onNavigateToSettings = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                SettingsActivity::class.java
                            )
                        )
                    })
                }
            }
        }

        // Broadcast receiver removed - use refresh button for now

        // Handle first launch
        // lifecycleScope.launch {
        //     val isFirstLaunch = preferences.isFirstLaunch.first()
        //     if (isFirstLaunch) {
        //         // Show welcome dialog or handle first launch
        //         preferences.setFirstLaunch(false)
        //     }
        // }
    }
}
