package com.ko.app.data.repository

import androidx.lifecycle.LiveData
import com.ko.app.data.dao.ScreenshotDao
import com.ko.app.data.entity.Screenshot
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
class ScreenshotRepository(private val screenshotDao: ScreenshotDao) {

    fun getAllScreenshots(): Flow<List<Screenshot>> {
        return screenshotDao.getAllScreenshots()
    }

    fun getAllScreenshotsLiveData(): LiveData<List<Screenshot>> {
        return screenshotDao.getAllScreenshotsLiveData()
    }

    fun getMarkedScreenshots(): Flow<List<Screenshot>> {
        return screenshotDao.getMarkedScreenshots()
    }

    fun getMarkedScreenshotsLiveData(): LiveData<List<Screenshot>> {
        return screenshotDao.getMarkedScreenshotsLiveData()
    }

    fun getKeptScreenshots(): Flow<List<Screenshot>> {
        return screenshotDao.getKeptScreenshots()
    }

    fun getUnmarkedScreenshots(): Flow<List<Screenshot>> {
        return screenshotDao.getUnmarkedScreenshots()
    }

    fun getMarkedCount(): Flow<Int> {
        return screenshotDao.getMarkedCount()
    }

    suspend fun insert(screenshot: Screenshot): Long {
        return screenshotDao.insert(screenshot)
    }

    suspend fun update(screenshot: Screenshot) {
        screenshotDao.update(screenshot)
    }

    suspend fun delete(screenshot: Screenshot) {
        screenshotDao.delete(screenshot)
    }

    suspend fun getById(id: Long): Screenshot? {
        return screenshotDao.getById(id)
    }

    suspend fun getByFilePath(filePath: String): Screenshot? {
        return screenshotDao.getByFilePath(filePath)
    }

    suspend fun getExpiredScreenshots(currentTime: Long): List<Screenshot> {
        return screenshotDao.getExpiredScreenshots(currentTime)
    }

    suspend fun deleteById(id: Long) {
        screenshotDao.deleteById(id)
    }

    suspend fun deleteByFilePath(filePath: String) {
        screenshotDao.deleteByFilePath(filePath)
    }

    suspend fun markAsKept(id: Long) {
        screenshotDao.markAsKept(id)
    }

    suspend fun markForDeletion(id: Long, deletionTime: Long) {
        screenshotDao.markForDeletion(id, deletionTime)
    }

    suspend fun deleteAll() {
        screenshotDao.deleteAll()
    }
}

