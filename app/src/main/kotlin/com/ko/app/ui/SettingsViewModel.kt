package com.ko.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ko.app.data.preferences.AppPreferences
import com.ko.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    val currentTheme = preferences.themeMode.map { themeString ->
        when (themeString) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "dynamic" -> ThemeMode.DYNAMIC
            "oled" -> ThemeMode.OLED
            else -> ThemeMode.SYSTEM
        }
    }

    val deletionTime = preferences.deletionTimeMillis

    val isManualMode = preferences.isManualMarkMode

    val language = preferences.language

    val screenshotFolderUri = preferences.screenshotFolderUri

    val autoCleanupEnabled = preferences.autoCleanupEnabled

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            val themeString = when (themeMode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
                ThemeMode.DYNAMIC -> "dynamic"
                ThemeMode.OLED -> "oled"
            }
            preferences.setThemeMode(themeString)
        }
    }

    fun setDeletionTime(timeMillis: Long) {
        viewModelScope.launch {
            preferences.setDeletionTimeMillis(timeMillis)
        }
    }

    fun setManualMode(manual: Boolean) {
        viewModelScope.launch {
            preferences.setManualMarkMode(manual)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            preferences.setLanguage(language)
        }
    }

    fun setAutoCleanupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoCleanupEnabled(enabled)
        }
    }


}
