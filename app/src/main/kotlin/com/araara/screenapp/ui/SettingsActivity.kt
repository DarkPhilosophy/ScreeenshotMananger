package com.araara.screenapp.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.araara.screenapp.LanguageManager
import com.araara.screenapp.data.preferences.AppPreferences
import com.araara.screenapp.ui.theme.AppTheme
import com.araara.screenapp.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                // Save the URI
                lifecycleScope.launch {
                    preferences.setScreenshotFolderUri(it.toString())
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var themeModeString by remember { mutableStateOf("system") }
            LaunchedEffect(Unit) {
                preferences.themeMode.collect { themeModeString = it }
            }
            val themeMode = when (themeModeString) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "dynamic" -> ThemeMode.DYNAMIC
                "oled" -> ThemeMode.OLED
                else -> ThemeMode.SYSTEM
            }

            // Use cached language from LanguageManager to avoid disk I/O
            val language by preferences.language.collectAsState(initial = LanguageManager.getCurrentLanguage())

            // Apply locale synchronously to activity resources
            val localeTag = if (language == "ro") "ro" else "en"
            val newLocale = Locale.forLanguageTag(localeTag)

            // Update activity resources immediately
            @Suppress("DEPRECATION")
            resources.updateConfiguration(
                resources.configuration.apply { setLocale(newLocale) },
                resources.displayMetrics
            )

            // Observe language changes for system-wide locale updates
            LaunchedEffect(language) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = getSystemService(android.app.LocaleManager::class.java)
                    localeManager?.applicationLocales =
                        android.os.LocaleList.forLanguageTags(localeTag)
                } else {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(
                            localeTag
                        )
                    )
                    // Restart the app for older APIs
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
            }

            CompositionLocalProvider(LocalConfiguration provides Configuration(resources.configuration)) {
                // Force recomposition when language changes
                AppTheme(themeMode = themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsScreen(
                            onNavigateBack = { finish() },
                            onSelectScreenshotFolder = { folderPicker.launch(null) },
                            onNavigateToConsole = {
                                startActivity(
                                    Intent(
                                        this@SettingsActivity,
                                        DebugConsoleActivity::class.java
                                    )
                                )
                            },
                            onThemeChangeCallback = { themeModeString = it }
                        )
                    }
                }
            }
        }
    }
}