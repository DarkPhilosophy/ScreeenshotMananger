package ro.snapify.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ro.snapify.data.entity.MediaItem
import ro.snapify.data.model.FilterState
import ro.snapify.data.model.ScreenshotTab
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.data.repository.MediaRepository
import ro.snapify.service.ScreenshotMonitorService
import ro.snapify.util.DebugLogger
import ro.snapify.util.NotificationHelper
import ro.snapify.util.PermissionUtils
import javax.inject.Inject

enum class MonitoringStatus {
    STOPPED,
    ACTIVE,
    MISSING_PERMISSIONS
}

enum class RefreshReason {
    Other
}

data class MainUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val showPermissionDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showOperationModeDialog: Boolean = false,
    val showDeletionTimeDialog: Boolean = false,
    val videoPreviewItem: MediaItem? = null,
    val imagePreviewItem: MediaItem? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
    private val refreshFlow: MutableSharedFlow<RefreshReason>
) : ViewModel() {

    companion object {
        val newItemFlow = MutableSharedFlow<MediaItem>(replay = 1)
        val deleteItemFlow = MutableSharedFlow<Long>(replay = 1)
    }


    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _mediaItems: SnapshotStateList<MediaItem> = mutableStateListOf()
    val mediaItems = _mediaItems

    private var currentOffset = 0
    private val pageSize = 20
    private var hasMore = true

    private val _refreshTrigger = MutableStateFlow(0L)
    val refreshTrigger = _refreshTrigger.asStateFlow()

    val currentTime = MutableStateFlow(System.currentTimeMillis())

    val liveVideoPreviewEnabled = preferences.liveVideoPreviewEnabled

    private val _monitoringStatus = MutableStateFlow(MonitoringStatus.STOPPED)
    val monitoringStatus = _monitoringStatus.asStateFlow()

    private val _newScreenshotDetected = MutableSharedFlow<Unit>(replay = 0)
    val newScreenshotDetected = _newScreenshotDetected.asSharedFlow()

    private val _deletingIds = MutableStateFlow(setOf<Long>())
    val deletingIds = _deletingIds.asStateFlow()

    val mediaFolderUris = preferences.mediaFolderUris

    private val initialSelectedFolders = runBlocking {
        val loaded = preferences.selectedFolders.first()
        val configuredUris = preferences.mediaFolderUris.first()
        val parsedConfigured = configuredUris.mapNotNull { getFolderPathFromUri(it) }.toSet()
        if (loaded.isNotEmpty() || parsedConfigured.isEmpty()) loaded else parsedConfigured
    }

    private val _currentFilterState =
        MutableStateFlow(FilterState(selectedFolders = initialSelectedFolders))
    val currentFilterState = _currentFilterState.asStateFlow()


    init {
        loadMediaItems()
        observeServiceStatus()
        observeRefreshEvents()
        observeNewItems()
        observeDeleteItems()
        observeFolderChanges()
        startTimeUpdater()
        checkAndStartServiceOnLaunch()
    }

    private fun checkAndStartServiceOnLaunch() {
        viewModelScope.launch {
            val isEnabled = preferences.serviceEnabled.first()
            val configuredUris = preferences.mediaFolderUris.first()
            if (isEnabled && configuredUris.isNotEmpty()) {
                val missingPermissions = PermissionUtils.getMissingPermissions(context)
                if (missingPermissions.isEmpty()) {
                    // Start the service since it's enabled, permissions granted, and folders configured
                    val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }

    private fun startTimeUpdater() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                currentTime.value = System.currentTimeMillis()
            }
        }
    }


    private fun observeDeleteItems() {
        viewModelScope.launch {
            deleteItemFlow.collect { id ->
                _deletingIds.value += id
                // Delay for animation
                kotlinx.coroutines.delay(500)
                _mediaItems.removeIf { it.id == id }
                _deletingIds.value -= id
            }
        }
    }

    private fun observeNewItems() {
        viewModelScope.launch {
            newItemFlow.collect { item ->
                DebugLogger.info(
                    "MainViewModel",
                    "observeNewItems: Adding new item ${item.fileName} (ID: ${item.id})"
                )
                _mediaItems.add(0, item)
                // Trigger new screenshot detected for UI feedback
                _newScreenshotDetected.emit(Unit)
                DebugLogger.info(
                    "MainViewModel",
                    "observeNewItems: Added item, mediaItems now has ${_mediaItems.size} items"
                )
            }
        }
    }

    private fun observeRefreshEvents() {
        viewModelScope.launch {
            DebugLogger.info(
                "MainViewModel",
                "observeRefreshEvents: Starting to observe refreshFlow"
            )
            refreshFlow.collect { reason ->
                val currentTime = System.currentTimeMillis()
                DebugLogger.info(
                    "MainViewModel",
                    "observeRefreshEvents: Processing refresh $reason, updating refreshTrigger to $currentTime"
                )
                _refreshTrigger.value = currentTime
                // Emit new screenshot detected event
                _newScreenshotDetected.emit(Unit)
                // Refresh current tab data
                loadMediaItems()
            }
        }
    }

    private fun updateMonitoringStatus() {
        viewModelScope.launch {
            val isEnabled = preferences.serviceEnabled.first()
            val status = if (!isEnabled) {
                MonitoringStatus.STOPPED
            } else {
                val missingPermissions = PermissionUtils.getMissingPermissions(context)
                if (missingPermissions.isEmpty()) {
                    MonitoringStatus.ACTIVE
                } else {
                    MonitoringStatus.MISSING_PERMISSIONS
                }
            }
            _monitoringStatus.value = status

            // If status is ACTIVE, ensure service is running
            if (status == MonitoringStatus.ACTIVE) {
                val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    private fun observeServiceStatus() {
        viewModelScope.launch {
            preferences.serviceEnabled.collect { isEnabled ->
                DebugLogger.info("MainViewModel", "Service status changed: $isEnabled")
                updateMonitoringStatus()
            }
        }
    }

    private fun observeFolderChanges() {
        viewModelScope.launch {
            preferences.mediaFolderUris.collect { folders ->
                DebugLogger.info("MainViewModel", "Media folders changed: $folders")
            }
        }
        viewModelScope.launch {
            preferences.selectedFolders.collect { folders ->
                val configuredUris = preferences.mediaFolderUris.first()
                val parsedConfigured =
                    configuredUris.mapNotNull { getFolderPathFromUri(it) }.toSet()
                val effectiveFolders =
                    if (folders.isNotEmpty() || parsedConfigured.isEmpty()) folders else parsedConfigured
                _currentFilterState.update { it.copy(selectedFolders = effectiveFolders) }
            }
        }
    }

    private fun getFolderPathFromUri(uri: String): String? {
        return try {
            val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
            when {
                decoded.contains("primary:") -> "/storage/emulated/0/" + decoded.substringAfter("primary:")
                    .replace("%2F", "/").replace("%3A", ":")

                decoded.contains("tree/") -> {
                    val parts = decoded.substringAfter("tree/").split(":")
                    if (parts.size >= 2) {
                        val volume = parts[0]
                        val path = parts[1].replace("%2F", "/").replace("%3A", ":")
                        "/storage/emulated/0/$path" // Assuming primary for now
                    } else null
                }

                else -> null
            }?.removeSuffix("/") // Remove trailing slash if any
        } catch (e: Exception) {
            null
        }
    }

    fun updateTagSelection(selectedTags: Set<ScreenshotTab>) {
        _currentFilterState.update { it.copy(selectedTags = selectedTags) }
        loadMediaItems()
    }

    fun updateFolderSelection(selectedFolders: Set<String>) {
        viewModelScope.launch {
            preferences.setSelectedFolders(selectedFolders)
        }
        // State update handled by collect
    }

    fun loadMediaItems() {
        viewModelScope.launch {
            try {
                DebugLogger.info(
                    "MainViewModel",
                    "loadMediaItems: Starting to load media items - THIS WILL REPLACE ENTIRE LIST"
                )
                _uiState.update { it.copy(isLoading = true) }
                currentOffset = 0
                hasMore = true

                // Get all media items (filter in UI)
                val allMediaItems = repository.getAllMediaItems().first()

                // Sort by newest first (in case database order is not correct)
                val sortedItems = allMediaItems.sortedByDescending { it.createdAt }

                DebugLogger.info(
                    "MainViewModel",
                    "loadMediaItems: Clearing ${_mediaItems.size} existing items and adding ${sortedItems.size} new items"
                )
                _mediaItems.clear()
                _mediaItems.addAll(sortedItems)
                currentOffset = sortedItems.size
                hasMore = false

                DebugLogger.info(
                    "MainViewModel",
                    "loadMediaItems: COMPLETED - mediaItems now has ${_mediaItems.size} items"
                )
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error loading media items", e)
                _uiState.update { it.copy(message = "Failed to load media items") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMoreMediaItems() {
        if (!hasMore) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val allNewMediaItems = repository.getPagedMediaItems(currentOffset, pageSize)

                // Apply filters
                val currentState = _currentFilterState.value
                val filteredNewItems = allNewMediaItems.filter { item ->
                    // Folder filter
                    val folderMatches = currentState.selectedFolders.any { selectedPath ->
                        item.filePath.lowercase().startsWith(selectedPath.lowercase())
                    }

                    // Tag filter
                    val tagMatches =
                        if (currentState.selectedTags.isEmpty() || currentState.isAllTagsSelected()) {
                            true
                        } else {
                            when {
                                ScreenshotTab.MARKED in currentState.selectedTags && item.deletionTimestamp != null && !item.isKept -> true
                                ScreenshotTab.KEPT in currentState.selectedTags && item.isKept -> true
                                ScreenshotTab.UNMARKED in currentState.selectedTags && item.deletionTimestamp == null && !item.isKept -> true
                                else -> false
                            }
                        }

                    folderMatches && tagMatches
                }

                if (filteredNewItems.isNotEmpty()) {
                    // Sort new items and add to existing sorted list
                    val sortedNewItems = filteredNewItems.sortedByDescending { it.createdAt }
                    _mediaItems.addAll(sortedNewItems)
                    currentOffset += allNewMediaItems.size // Update offset based on unfiltered count for next query
                    hasMore = allNewMediaItems.size >= pageSize
                } else {
                    hasMore = false
                }

                DebugLogger.info(
                    "MainViewModel",
                    "Loaded ${filteredNewItems.size} more filtered media items"
                )
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error loading more media items", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshMediaItems() {
        DebugLogger.info("MainViewModel", "refreshMediaItems: Called - triggering full refresh")
        _refreshTrigger.value = System.currentTimeMillis()
        loadMediaItems()
    }


    fun keepMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                repository.markAsKept(mediaItem.id)
                NotificationHelper.cancelNotification(context, mediaItem.id.toInt())

                // Update the item in the UI list
                val index = _mediaItems.indexOfFirst { it.id == mediaItem.id }
                if (index != -1) {
                    _mediaItems.removeAt(index)
                    _mediaItems.add(index, mediaItem.copy(isKept = true, deletionTimestamp = null))
                }

                // Log media keep event to Firebase Analytics
                val bundle = android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_ID, mediaItem.id.toString())
                    putString(FirebaseAnalytics.Param.ITEM_NAME, mediaItem.fileName)
                    putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
                }
                FirebaseAnalytics.getInstance(context).logEvent("media_keep", bundle)

                _uiState.update { it.copy(message = "Media item kept") }
                DebugLogger.info("MainViewModel", "Media item ${mediaItem.id} marked as kept")
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error keeping media item", e)
                _uiState.update { it.copy(message = "Failed to keep media item") }
            }
        }
    }

    fun unkeepMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                repository.markAsUnkept(mediaItem.id)

                // Update the item in the UI list
                val index = _mediaItems.indexOfFirst { it.id == mediaItem.id }
                if (index != -1) {
                    _mediaItems.removeAt(index)
                    _mediaItems.add(index, mediaItem.copy(isKept = false, deletionTimestamp = null))
                }

                // Log media unkeep event to Firebase Analytics
                val bundle = android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_ID, mediaItem.id.toString())
                    putString(FirebaseAnalytics.Param.ITEM_NAME, mediaItem.fileName)
                    putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
                }
                FirebaseAnalytics.getInstance(context).logEvent("media_unkeep", bundle)

                _uiState.update { it.copy(message = "Media item unkept") }
                DebugLogger.info("MainViewModel", "Media item ${mediaItem.id} marked as unkept")
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error unkeeping media item", e)
                _uiState.update { it.copy(message = "Failed to unkeep media item") }
            }
        }
    }

    fun deleteMediaItem(mediaItem: MediaItem) {
        // Mark as deleting for animation
        _deletingIds.value += mediaItem.id

        viewModelScope.launch {
            try {
                // Wait for animation to complete
                kotlinx.coroutines.delay(500)

                // Try to delete file first
                mediaItem.contentUri?.let { uriStr ->
                    try {
                        val uri = uriStr.toUri()
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        DebugLogger.warning(
                            "MainViewModel",
                            "ContentResolver delete failed: ${e.message}"
                        )
                    }
                }

                // Also try file delete
                val file = java.io.File(mediaItem.filePath)
                if (file.exists()) {
                    file.delete()
                }

                // Always remove from database
                repository.deleteById(mediaItem.id)
                NotificationHelper.cancelNotification(context, mediaItem.id.toInt())

                // Remove from the UI list
                _mediaItems.removeIf { it.id == mediaItem.id }

                // Log media delete event to Firebase Analytics
                val bundle = android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_ID, mediaItem.id.toString())
                    putString(FirebaseAnalytics.Param.ITEM_NAME, mediaItem.fileName)
                    putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
                }
                FirebaseAnalytics.getInstance(context).logEvent("media_delete", bundle)

                _uiState.update { it.copy(message = "Media item deleted") }
                DebugLogger.info("MainViewModel", "Media item ${mediaItem.id} deleted")

                // Remove from deleting ids
                _deletingIds.value -= mediaItem.id
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error deleting media item", e)
                _uiState.update { it.copy(message = "Failed to delete media item") }
                // Remove from deleting ids on error
                _deletingIds.value -= mediaItem.id
            }
        }
    }

    fun openMediaItem(mediaItem: MediaItem) {
        val isVideo = mediaItem.filePath.lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(
                ".webm"
            )
        }

        if (isVideo) {
            openVideoPreview(mediaItem)
        } else {
            openImagePreview(mediaItem)
        }
    }

    private fun openExternal(mediaItem: MediaItem, mimeType: String) {
        try {
            // Log media view event to Firebase Analytics
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, mediaItem.id.toString())
                putString(FirebaseAnalytics.Param.ITEM_NAME, mediaItem.fileName)
                putString(
                    FirebaseAnalytics.Param.CONTENT_TYPE,
                    if (mimeType.startsWith("image")) "image" else "video"
                )
            }
            FirebaseAnalytics.getInstance(context)
                .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)

            val uri = mediaItem.contentUri?.toUri() ?: run {
                val file = java.io.File(mediaItem.filePath)
                if (!file.exists()) {
                    _uiState.update { it.copy(message = "Media file not found") }
                    return
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(
                Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            DebugLogger.error("MainViewModel", "Error opening media item", e)
            _uiState.update { it.copy(message = "Failed to open media item") }
        }
    }


    private fun startMonitoringService() {
        viewModelScope.launch {
            preferences.setServiceEnabled(true)
            refreshFlow.emit(RefreshReason.Other) // Trigger status update

            val missingPermissions = PermissionUtils.getMissingPermissions(context)
            if (missingPermissions.isNotEmpty()) {
                // Permissions missing, show dialog but don't start service (it will stop itself)
                showPermissionsDialog()
                return@launch
            }

            val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            _uiState.update { it.copy(message = "Screenshot monitoring started") }
        }
    }

    private fun stopMonitoringService() {
        viewModelScope.launch {
            preferences.setServiceEnabled(false)
            refreshFlow.emit(RefreshReason.Other) // Trigger status update

            val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
            context.stopService(serviceIntent)

            _uiState.update { it.copy(message = "Screenshot monitoring stopped") }
        }
    }


    fun refreshMonitoringStatus() {
        updateMonitoringStatus()
    }

    fun startMonitoring() {
        startMonitoringService()
    }

    fun stopMonitoring() {
        stopMonitoringService()
    }

    fun showPermissionsDialog() {
        _uiState.update { it.copy(showPermissionDialog = true) }
    }

    fun hidePermissionsDialog() {
        _uiState.update { it.copy(showPermissionDialog = false) }
    }

    fun showThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = true) }
    }

    fun hideThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    fun showLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = true) }
    }

    fun hideLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = false) }
    }

    fun showOperationModeDialog() {
        _uiState.update { it.copy(showOperationModeDialog = true) }
    }

    fun hideOperationModeDialog() {
        _uiState.update { it.copy(showOperationModeDialog = false) }
    }

    fun showDeletionTimeDialog() {
        _uiState.update { it.copy(showDeletionTimeDialog = true) }
    }

    fun hideDeletionTimeDialog() {
        _uiState.update { it.copy(showDeletionTimeDialog = false) }
    }


    fun dismissWelcomeDialog() {
        _uiState.update { it.copy(showWelcomeDialog = false) }
    }

    fun openVideoPreview(mediaItem: MediaItem) {
        _uiState.update { it.copy(videoPreviewItem = mediaItem) }
    }

    fun closeVideoPreview() {
        _uiState.update { it.copy(videoPreviewItem = null) }
    }

    fun openImagePreview(mediaItem: MediaItem) {
        _uiState.update { it.copy(imagePreviewItem = mediaItem) }
    }

    fun closeImagePreview() {
        _uiState.update { it.copy(imagePreviewItem = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
