package com.ko.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_DELETION_TIME_MILLIS = longPreferencesKey("deletion_time_millis")
        private val KEY_MANUAL_MARK_MODE = booleanPreferencesKey("manual_mark_mode")
        private val KEY_SCREENSHOT_FOLDER = stringPreferencesKey("screenshot_folder")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")

        const val DEFAULT_DELETION_TIME_MILLIS = 15 * 60 * 1000L
        const val DEFAULT_SCREENSHOT_FOLDER = "Pictures/Screenshots"
    }

    val deletionTimeMillis: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_DELETION_TIME_MILLIS] ?: DEFAULT_DELETION_TIME_MILLIS
    }

    val isManualMarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MANUAL_MARK_MODE] ?: false
    }

    val screenshotFolder: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SCREENSHOT_FOLDER] ?: DEFAULT_SCREENSHOT_FOLDER
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVICE_ENABLED] ?: false
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_LAUNCH] ?: true
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

    suspend fun setScreenshotFolder(folder: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SCREENSHOT_FOLDER] = folder
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setFirstLaunch(isFirst: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH] = isFirst
        }
    }

    suspend fun getDeletionTimeMillisSync(): Long {
        var result = DEFAULT_DELETION_TIME_MILLIS
        context.dataStore.edit { preferences ->
            result = preferences[KEY_DELETION_TIME_MILLIS] ?: DEFAULT_DELETION_TIME_MILLIS
        }
        return result
    }

    suspend fun isManualMarkModeSync(): Boolean {
        var result = false
        context.dataStore.edit { preferences ->
            result = preferences[KEY_MANUAL_MARK_MODE] ?: false
        }
        return result
    }
}
