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

@Suppress("TooManyFunctions")
@Dao
interface ScreenshotDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: Screenshot): Long

    @Update
    suspend fun update(screenshot: Screenshot)

    @Delete
    suspend fun delete(screenshot: Screenshot)

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getById(id: Long): Screenshot?

    @Query("SELECT * FROM screenshots WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): Screenshot?

    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    fun getAllScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    fun getAllScreenshotsLiveData(): LiveData<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE isMarkedForDeletion = 1 ORDER BY deletionTimestamp ASC")
    fun getMarkedScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE isMarkedForDeletion = 1 ORDER BY deletionTimestamp ASC")
    fun getMarkedScreenshotsLiveData(): LiveData<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE isKept = 1 ORDER BY createdAt DESC")
    fun getKeptScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE isMarkedForDeletion = 0 AND isKept = 0 ORDER BY createdAt DESC")
    fun getUnmarkedScreenshots(): Flow<List<Screenshot>>

    @Query("SELECT * FROM screenshots WHERE deletionTimestamp <= :currentTime AND isMarkedForDeletion = 1")
    suspend fun getExpiredScreenshots(currentTime: Long): List<Screenshot>

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM screenshots WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM screenshots")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM screenshots WHERE isMarkedForDeletion = 1")
    fun getMarkedCount(): Flow<Int>

    @Query("UPDATE screenshots SET isMarkedForDeletion = 0, isKept = 1, deletionTimestamp = NULL WHERE id = :id")
    suspend fun markAsKept(id: Long)

    @Query("UPDATE screenshots SET isMarkedForDeletion = 1, deletionTimestamp = :deletionTime WHERE id = :id")
    suspend fun markForDeletion(id: Long, deletionTime: Long)

    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllScreenshotsPaged(limit: Int, offset: Int): List<Screenshot>

    @Query("SELECT * FROM screenshots WHERE isMarkedForDeletion = 1 ORDER BY deletionTimestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMarkedScreenshotsPaged(limit: Int, offset: Int): List<Screenshot>

    @Query("SELECT * FROM screenshots WHERE isKept = 1 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getKeptScreenshotsPaged(limit: Int, offset: Int): List<Screenshot>
}
