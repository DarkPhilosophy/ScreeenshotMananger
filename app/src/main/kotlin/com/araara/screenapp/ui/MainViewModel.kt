package com.araara.screenapp.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araara.screenapp.data.entity.Screenshot
import com.araara.screenapp.data.preferences.AppPreferences
import com.araara.screenapp.data.repository.ScreenshotRepository
import com.araara.screenapp.service.ScreenshotMonitorService
import com.araara.screenapp.util.DebugLogger
import com.araara.screenapp.util.NotificationHelper
import com.araara.screenapp.util.PermissionUtils
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MonitoringStatus {
    STOPPED,
    ACTIVE,
    MISSING_PERMISSIONS
}

data class MainUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val showPermissionDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
    private val refreshFlow: MutableSharedFlow<Unit>
) : ViewModel() {

    var onNavigateToSettings: (() -> Unit)? = null


    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _currentTab = MutableStateFlow(ScreenshotTab.ALL)
    val currentTab = _currentTab.asStateFlow()

    private val _screenshots = MutableStateFlow<List<Screenshot>>(emptyList())
    val screenshots = _screenshots.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)
    val refreshTrigger = _refreshTrigger.asStateFlow()

    val currentTime = MutableStateFlow(System.currentTimeMillis())

    private val _monitoringStatus = MutableStateFlow(MonitoringStatus.STOPPED)
    val monitoringStatus = _monitoringStatus.asStateFlow()


    private var currentOffset = 0
    private val pageSize = 20
    private var hasMore = true

    init {
        loadScreenshots()
        observeServiceStatus()
        observeRefreshEvents()
        startTimeUpdater()
        checkAndStartServiceOnLaunch()
    }

    private fun checkAndStartServiceOnLaunch() {
        viewModelScope.launch {
            val isEnabled = preferences.serviceEnabled.first()
            if (isEnabled) {
                val missingPermissions = PermissionUtils.getMissingPermissions(context)
                if (missingPermissions.isEmpty()) {
                    // Start the service since it's enabled and permissions are granted
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

    private fun observeRefreshEvents() {
        viewModelScope.launch {
            DebugLogger.info(
                "MainViewModel",
                "observeRefreshEvents: Starting to observe refreshFlow"
            )
            refreshFlow.collect {
                DebugLogger.info(
                    "MainViewModel",
                    "observeRefreshEvents: Received refreshFlow, updating refreshTrigger and reloading screenshots"
                )
                _refreshTrigger.value = System.currentTimeMillis()
                // Refresh current tab data
                loadScreenshots()
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

    fun selectTab(tab: ScreenshotTab) {
        _currentTab.value = tab
        loadScreenshots() // Load initial data
        // Realtime updates will handle subsequent changes
    }

    fun refreshScreenshots() {
        _refreshTrigger.value = System.currentTimeMillis()
        loadScreenshots()
    }

    fun loadScreenshots() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                currentOffset = 0
                hasMore = true

                val newScreenshots = when (_currentTab.value) {
                    ScreenshotTab.MARKED -> repository.getPagedMarkedScreenshots(0, pageSize)
                    ScreenshotTab.KEPT -> repository.getPagedKeptScreenshots(0, pageSize)
                    ScreenshotTab.UNMARKED -> repository.getPagedUnmarkedScreenshots(0, pageSize)
                    ScreenshotTab.ALL -> repository.getPagedScreenshots(0, pageSize)
                }

                _screenshots.value = newScreenshots
                currentOffset = newScreenshots.size
                hasMore = newScreenshots.size >= pageSize

                DebugLogger.info(
                    "MainViewModel",
                    "Loaded ${newScreenshots.size} screenshots for tab ${_currentTab.value}, total in state: ${_screenshots.value.size}"
                )
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error loading screenshots", e)
                _uiState.update { it.copy(message = "Failed to load screenshots") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMoreScreenshots() {
        if (_uiState.value.isLoading || !hasMore) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val newScreenshots = when (_currentTab.value) {
                    ScreenshotTab.MARKED -> repository.getPagedMarkedScreenshots(
                        currentOffset,
                        pageSize
                    )

                    ScreenshotTab.KEPT -> repository.getPagedKeptScreenshots(
                        currentOffset,
                        pageSize
                    )

                    ScreenshotTab.UNMARKED -> repository.getPagedUnmarkedScreenshots(
                        currentOffset,
                        pageSize
                    )

                    ScreenshotTab.ALL -> repository.getPagedScreenshots(currentOffset, pageSize)
                }

                if (newScreenshots.isNotEmpty()) {
                    _screenshots.update { it + newScreenshots }
                    currentOffset += newScreenshots.size
                    hasMore = newScreenshots.size >= pageSize
                } else {
                    hasMore = false
                }

                DebugLogger.info("MainViewModel", "Loaded ${newScreenshots.size} more screenshots")
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error loading more screenshots", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun keepScreenshot(screenshot: Screenshot) {
        viewModelScope.launch {
            try {
                repository.markAsKept(screenshot.id)
                NotificationHelper.cancelNotification(context, screenshot.id.toInt())

                // Log screenshot keep event to Firebase Analytics
                val bundle = android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_ID, screenshot.id.toString())
                    putString(FirebaseAnalytics.Param.ITEM_NAME, screenshot.fileName)
                    putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
                }
                FirebaseAnalytics.getInstance(context).logEvent("screenshot_keep", bundle)

                // Update local list
                _screenshots.update { list ->
                    list.map {
                        if (it.id == screenshot.id) {
                            it.copy(isKept = true, deletionTimestamp = null)
                        } else it
                    }
                }

                _uiState.update { it.copy(message = "Screenshot kept") }
                DebugLogger.info("MainViewModel", "Screenshot ${screenshot.id} marked as kept")
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error keeping screenshot", e)
                _uiState.update { it.copy(message = "Failed to keep screenshot") }
            }
        }
    }

    fun deleteScreenshot(screenshot: Screenshot) {
        viewModelScope.launch {
            try {
                // Try to delete file first
                screenshot.contentUri?.let { uriStr ->
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
                val file = java.io.File(screenshot.filePath)
                if (file.exists()) {
                    file.delete()
                }

                // Always remove from database
                repository.deleteById(screenshot.id)
                NotificationHelper.cancelNotification(context, screenshot.id.toInt())

                // Log screenshot delete event to Firebase Analytics
                val bundle = android.os.Bundle().apply {
                    putString(FirebaseAnalytics.Param.ITEM_ID, screenshot.id.toString())
                    putString(FirebaseAnalytics.Param.ITEM_NAME, screenshot.fileName)
                    putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
                }
                FirebaseAnalytics.getInstance(context).logEvent("screenshot_delete", bundle)

                // Update local list
                _screenshots.update { list ->
                    list.filter { it.id != screenshot.id }
                }

                _uiState.update { it.copy(message = "Screenshot deleted") }
                DebugLogger.info("MainViewModel", "Screenshot ${screenshot.id} deleted")
            } catch (e: Exception) {
                DebugLogger.error("MainViewModel", "Error deleting screenshot", e)
                _uiState.update { it.copy(message = "Failed to delete screenshot") }
            }
        }
    }

    fun openScreenshot(screenshot: Screenshot) {
        try {
            // Log screenshot view event to Firebase Analytics
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, screenshot.id.toString())
                putString(FirebaseAnalytics.Param.ITEM_NAME, screenshot.fileName)
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
            }
            FirebaseAnalytics.getInstance(context)
                .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)

            val uri = screenshot.contentUri?.toUri() ?: run {
                val file = java.io.File(screenshot.filePath)
                if (!file.exists()) {
                    _uiState.update { it.copy(message = "Screenshot file not found") }
                    return
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(
                Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            DebugLogger.error("MainViewModel", "Error opening screenshot", e)
            _uiState.update { it.copy(message = "Failed to open screenshot") }
        }
    }


    private fun startMonitoringService() {
        viewModelScope.launch {
            preferences.setServiceEnabled(true)
            refreshFlow.emit(Unit) // Trigger status update

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
            refreshFlow.emit(Unit) // Trigger status update

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

    fun navigateToSettings() {
        onNavigateToSettings?.invoke()
    }

    fun dismissWelcomeDialog() {
        _uiState.update { it.copy(showWelcomeDialog = false) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
