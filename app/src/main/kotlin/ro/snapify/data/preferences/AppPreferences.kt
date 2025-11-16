package ro.snapify.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ro.snapify.data.model.FilterState
import java.util.Locale
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val KEY_DELETION_TIME_MILLIS = longPreferencesKey("deletion_time_millis")
        private val KEY_MANUAL_MARK_MODE = booleanPreferencesKey("manual_mark_mode")
        private val KEY_MEDIA_FOLDER_URIS = stringSetPreferencesKey("media_folder_uris")

        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")

        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AUTO_CLEANUP_ENABLED = booleanPreferencesKey("auto_cleanup_enabled")
        private val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        private val KEY_TUTORIAL_SHOWN = booleanPreferencesKey("tutorial_shown")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_LIVE_VIDEO_PREVIEW_ENABLED =
            booleanPreferencesKey("live_video_preview_enabled")
        private val KEY_PERMANENT_SETTING_MENU_ENABLED =
            booleanPreferencesKey("permanent_setting_menu_enabled")
        private val KEY_SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
        private val KEY_FOLDER_FILTER_STATES =
            stringPreferencesKey("folder_filter_states") // JSON string of folder -> FilterState map
        private val KEY_HAS_INITIALIZED_FOLDERS = booleanPreferencesKey("has_initialized_folders")

        const val DEFAULT_DELETION_TIME_MILLIS = 1 * 60 * 1000L
        val DEFAULT_MEDIA_FOLDER_URIS = emptySet<String>() // No default folders
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
        preferences[KEY_MANUAL_MARK_MODE] ?: true  // Default to Manual mode
    }

    val mediaFolderUris: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_MEDIA_FOLDER_URIS] ?: DEFAULT_MEDIA_FOLDER_URIS
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

    val developerModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVELOPER_MODE_ENABLED] ?: false
    }

    val tutorialShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TUTORIAL_SHOWN] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true  // Default to enabled
    }

    val liveVideoPreviewEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LIVE_VIDEO_PREVIEW_ENABLED] ?: false  // Default to disabled
    }

    val permanentSettingMenuEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PERMANENT_SETTING_MENU_ENABLED] ?: false  // Default to disabled
    }

    val selectedFolders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_FOLDERS] ?: emptySet()
    }

    val folderFilterStates: Flow<Map<String, FilterState>> =
        context.dataStore.data.map { preferences ->
            val jsonString = preferences[KEY_FOLDER_FILTER_STATES] ?: "{}"
            try {
                val type = object : TypeToken<Map<String, FilterState>>() {}.type
                Gson().fromJson(jsonString, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap() // Return empty map on error
            }
        }

    val hasInitializedFolders: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_HAS_INITIALIZED_FOLDERS] ?: false
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

    suspend fun setMediaFolderUris(uris: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MEDIA_FOLDER_URIS] = uris
        }
    }

    suspend fun setSelectedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_FOLDERS] = folders
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

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    suspend fun setTutorialShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TUTORIAL_SHOWN] = shown
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setFolderFilterStates(states: Map<String, FilterState>) {
        val jsonString = Gson().toJson(states)
        context.dataStore.edit { preferences ->
            preferences[KEY_FOLDER_FILTER_STATES] = jsonString
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

    suspend fun setLiveVideoPreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LIVE_VIDEO_PREVIEW_ENABLED] = enabled
        }
    }

    suspend fun setPermanentSettingMenuEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERMANENT_SETTING_MENU_ENABLED] = enabled
        }
    }

    suspend fun setHasInitializedFolders(initialized: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAS_INITIALIZED_FOLDERS] = initialized
        }
    }

}
