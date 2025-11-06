package com.ko.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val KEY_DELETION_TIME_MILLIS = longPreferencesKey("deletion_time_millis")
        private val KEY_MANUAL_MARK_MODE = booleanPreferencesKey("manual_mark_mode")
        private val KEY_SCREENSHOT_FOLDER_URI = stringPreferencesKey("screenshot_folder_uri")

        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AUTO_CLEANUP_ENABLED = booleanPreferencesKey("auto_cleanup_enabled")

        const val DEFAULT_DELETION_TIME_MILLIS = 1 * 60 * 1000L
        const val DEFAULT_SCREENSHOT_FOLDER_URI = "" // Empty means use default path
    }

    private fun getDeviceLanguage(): String {
        val deviceLocale = Locale.getDefault()
        return when {
            deviceLocale.language.startsWith("ro") -> "ro"
            else -> "en"
        }
    }

    val deletionTimeMillis: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_DELETION_TIME_MILLIS] ?: DEFAULT_DELETION_TIME_MILLIS
    }

    val isManualMarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MANUAL_MARK_MODE] ?: false
    }

    val screenshotFolderUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SCREENSHOT_FOLDER_URI] ?: DEFAULT_SCREENSHOT_FOLDER_URI
    }


    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVICE_ENABLED] ?: false
    }



    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LANGUAGE] ?: getDeviceLanguage()
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
    preferences[KEY_THEME_MODE] ?: "system"
    }

    val autoCleanupEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CLEANUP_ENABLED] ?: false
    }

    suspend fun setDeletionTimeMillis(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DELETION_TIME_MILLIS] = timeMillis
        }
    }

    suspend fun setManualMarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MANUAL_MARK_MODE] = enabled
        }
    }

    suspend fun setScreenshotFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SCREENSHOT_FOLDER_URI] = uri
        }
    }


    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ENABLED] = enabled
        }
    }



    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = lang
        }
    }

    suspend fun setAutoCleanupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_CLEANUP_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(themeMode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = themeMode
        }
    }


    suspend fun getLanguageSync(): String {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LANGUAGE] ?: getDeviceLanguage()
    }


}
