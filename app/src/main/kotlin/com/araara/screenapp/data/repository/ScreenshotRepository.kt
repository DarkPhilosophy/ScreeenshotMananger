package com.araara.screenapp.data.repository

import androidx.lifecycle.LiveData
import com.araara.screenapp.data.entity.Screenshot
import kotlinx.coroutines.flow.Flow

/**
 * Public repository interface for screenshots.
 * Implementations live in the `:app` module (ScreenshotRepositoryImpl).
 */
@Suppress("TooManyFunctions")
interface ScreenshotRepository {
    fun getAllScreenshots(): Flow<List<Screenshot>>
    fun getAllScreenshotsLiveData(): LiveData<List<Screenshot>>
    fun getMarkedScreenshots(): Flow<List<Screenshot>>
    fun getMarkedScreenshotsLiveData(): LiveData<List<Screenshot>>
    fun getKeptScreenshots(): Flow<List<Screenshot>>
    fun getUnmarkedScreenshots(): Flow<List<Screenshot>>
    fun getMarkedCount(): Flow<Int>

    suspend fun insert(screenshot: Screenshot): Long
    suspend fun insertAll(screenshots: List<Screenshot>): List<Long>
    suspend fun update(screenshot: Screenshot)
    suspend fun delete(screenshot: Screenshot)
    suspend fun getById(id: Long): Screenshot?
    suspend fun getByFilePath(filePath: String): Screenshot?
    suspend fun getExpiredScreenshots(currentTime: Long): List<Screenshot>
    suspend fun deleteById(id: Long)
    suspend fun deleteByFilePath(filePath: String)
    suspend fun markAsKept(id: Long)
    suspend fun markForDeletion(id: Long, deletionTime: Long)
    suspend fun setDeletionWorkId(id: Long, workId: String?)
    suspend fun deleteAll()

    suspend fun getPagedScreenshots(offset: Int, limit: Int): List<Screenshot>
    suspend fun getPagedMarkedScreenshots(offset: Int, limit: Int): List<Screenshot>
    suspend fun getPagedKeptScreenshots(offset: Int, limit: Int): List<Screenshot>
    suspend fun getPagedUnmarkedScreenshots(offset: Int, limit: Int): List<Screenshot>
}
