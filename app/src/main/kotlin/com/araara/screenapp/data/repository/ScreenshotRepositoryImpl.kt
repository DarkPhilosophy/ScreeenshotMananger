package com.araara.screenapp.data.repository

import androidx.lifecycle.LiveData
import com.araara.screenapp.data.dao.ScreenshotDao
import com.araara.screenapp.data.entity.Screenshot
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScreenshotRepositoryImpl @Inject constructor(
    private val screenshotDao: ScreenshotDao
) : ScreenshotRepository {

    override fun getAllScreenshots(): Flow<List<Screenshot>> = screenshotDao.getAllScreenshots()

    override fun getAllScreenshotsLiveData(): LiveData<List<Screenshot>> =
        screenshotDao.getAllScreenshotsLiveData()

    override fun getMarkedScreenshots(): Flow<List<Screenshot>> =
        screenshotDao.getMarkedScreenshots()

    override fun getMarkedScreenshotsLiveData(): LiveData<List<Screenshot>> =
        screenshotDao.getMarkedScreenshotsLiveData()

    override fun getKeptScreenshots(): Flow<List<Screenshot>> = screenshotDao.getKeptScreenshots()

    override fun getUnmarkedScreenshots(): Flow<List<Screenshot>> =
        screenshotDao.getUnmarkedScreenshots()

    override fun getMarkedCount(): Flow<Int> = screenshotDao.getMarkedCount()

    override suspend fun insert(screenshot: Screenshot): Long = screenshotDao.insert(screenshot)

    override suspend fun insertAll(screenshots: List<Screenshot>): List<Long> =
        screenshotDao.insertAll(screenshots)

    override suspend fun update(screenshot: Screenshot) = screenshotDao.update(screenshot)

    override suspend fun delete(screenshot: Screenshot) = screenshotDao.delete(screenshot)

    override suspend fun getById(id: Long): Screenshot? = screenshotDao.getById(id)

    override suspend fun getByFilePath(filePath: String): Screenshot? =
        screenshotDao.getByFilePath(filePath)

    override suspend fun getExpiredScreenshots(currentTime: Long): List<Screenshot> =
        screenshotDao.getExpiredScreenshots(currentTime)

    override suspend fun deleteById(id: Long) = screenshotDao.deleteById(id)

    override suspend fun deleteByFilePath(filePath: String) =
        screenshotDao.deleteByFilePath(filePath)

    override suspend fun markAsKept(id: Long) = screenshotDao.markAsKept(id)

    override suspend fun markForDeletion(id: Long, deletionTime: Long) =
        screenshotDao.markForDeletion(id, deletionTime)

    override suspend fun setDeletionWorkId(id: Long, workId: String?) =
        screenshotDao.setDeletionWorkId(id, workId)

    override suspend fun deleteAll() = screenshotDao.deleteAll()

    override suspend fun getPagedScreenshots(offset: Int, limit: Int): List<Screenshot> =
        screenshotDao.getPagedScreenshots(offset, limit)

    override suspend fun getPagedMarkedScreenshots(offset: Int, limit: Int): List<Screenshot> =
        screenshotDao.getPagedMarkedScreenshots(offset, limit)

    override suspend fun getPagedKeptScreenshots(offset: Int, limit: Int): List<Screenshot> =
        screenshotDao.getPagedKeptScreenshots(offset, limit)

    override suspend fun getPagedUnmarkedScreenshots(offset: Int, limit: Int): List<Screenshot> =
        screenshotDao.getPagedUnmarkedScreenshots(offset, limit)
}
