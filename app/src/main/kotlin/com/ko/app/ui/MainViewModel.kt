package com.ko.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ko.app.ScreenshotApp
import com.ko.app.data.entity.Screenshot

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val app: ScreenshotApp) : ViewModel() {

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab

    private var screenshotsJob: Job? = null

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
        observeScreenshots()
    }

    fun observeScreenshots(): Flow<List<Screenshot>> {
        screenshotsJob?.cancel()
        screenshotsJob = viewModelScope.launch {
            when (_currentTab.value) {
                0 -> app.repository.getMarkedScreenshots()
                1 -> app.repository.getKeptScreenshots()
                else -> app.repository.getAllScreenshots()
            }.collect { screenshots ->
                // Emit the list
                // Since Flow, return it
            }
        }
        return when (_currentTab.value) {
            0 -> app.repository.getMarkedScreenshots()
            1 -> app.repository.getKeptScreenshots()
            else -> app.repository.getAllScreenshots()
        }
    }

    fun markAsKept(screenshotId: Long) {
        viewModelScope.launch {
            app.repository.markAsKept(screenshotId)
        }
    }

    fun deleteScreenshot(screenshotId: Long, filePath: String) {
        viewModelScope.launch {
            val file = java.io.File(filePath)
            if (file.exists()) {
                file.delete()
            }
            app.repository.deleteById(screenshotId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        screenshotsJob?.cancel()
    }
}
