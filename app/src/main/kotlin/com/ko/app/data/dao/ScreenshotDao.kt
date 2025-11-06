package com.ko.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ko.app.data.entity.Screenshot
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screenshot: Screenshot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(screenshots: List<Screenshot>): List<Long>

    @Update
    suspend fun update(screenshot: Screenshot)

    @Delete
    suspend fun delete(screenshot: Screenshot)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM screenshots WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM screenshots")
    suspend fun deleteAll()

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getById(id: Long): Screenshot?

    @Query("SELECT * FROM screenshots WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): Screenshot?

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp < :currentTime AND isKept = 0")
    suspend fun getExpiredScreenshots(currentTime: Long): List<Screenshot>

    @Query("UPDATE screenshots SET isKept = 1, deletionTimestamp = NULL, deletionWorkId = NULL WHERE id = :id")
    suspend fun markAsKept(id: Long)

    @Query("UPDATE screenshots SET deletionTimestamp = :deletionTime WHERE id = :id")
    suspend fun markForDeletion(id: Long, deletionTime: Long)

    @Query("UPDATE screenshots SET deletionWorkId = :workId WHERE id = :id")
    suspend fun setDeletionWorkId(id: Long, workId: String?)

    @Query("SELECT * FROM screenshots")
    fun getAllScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots")
    fun getAllScreenshotsLiveData(): LiveData<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getMarkedScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getMarkedScreenshotsLiveData(): LiveData<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE isKept = 1 ORDER BY createdAt DESC")
    fun getKeptScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp IS NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getUnmarkedScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT COUNT(*) FROM screenshots WHERE deletionTimestamp IS NOT NULL AND isKept = 0")
    fun getMarkedCount(): Flow<Int>

    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedScreenshots(offset: Int, limit: Int): List<Screenshot>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMarkedScreenshots(offset: Int, limit: Int): List<Screenshot>

    @Query("SELECT * FROM screenshots WHERE isKept = 1 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedKeptScreenshots(offset: Int, limit: Int): List<Screenshot>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp IS NULL AND isKept = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedUnmarkedScreenshots(offset: Int, limit: Int): List<Screenshot>
}
