package ro.snapify.data.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import ro.snapify.data.entity.MediaItem

/**
 * Public repository interface for screenshots.
 * Implementations live in the `:app` module (ScreenshotRepositoryImpl).
 */
@Suppress("TooManyFunctions")
interface MediaRepository {
    fun getAllMediaItems(): Flow<List<MediaItem>>
    fun getAllMediaItemsLiveData(): LiveData<List<MediaItem>>
    fun getMarkedMediaItems(): Flow<List<MediaItem>>
    fun getMarkedMediaItemsLiveData(): LiveData<List<MediaItem>>
    fun getKeptMediaItems(): Flow<List<MediaItem>>
    fun getUnmarkedMediaItems(): Flow<List<MediaItem>>
    fun getMarkedCount(): Flow<Int>

    suspend fun insert(mediaItem: MediaItem): Long
    suspend fun insertAll(mediaItems: List<MediaItem>): List<Long>
    suspend fun update(mediaItem: MediaItem)
    suspend fun delete(mediaItem: MediaItem)
    suspend fun getById(id: Long): MediaItem?
    suspend fun getByFilePath(filePath: String): MediaItem?
    suspend fun getExpiredMediaItems(currentTime: Long): List<MediaItem>
    suspend fun deleteById(id: Long)
    suspend fun deleteByFilePath(filePath: String)
    suspend fun markAsKept(id: Long)
    suspend fun markAsUnkept(id: Long)
    suspend fun markForDeletion(id: Long, deletionTime: Long)
    suspend fun setDeletionWorkId(id: Long, workId: String?)
    suspend fun deleteAll()

    suspend fun getPagedMediaItems(offset: Int, limit: Int): List<MediaItem>
    suspend fun getPagedMarkedMediaItems(offset: Int, limit: Int): List<MediaItem>
    suspend fun getPagedKeptMediaItems(offset: Int, limit: Int): List<MediaItem>
    suspend fun getPagedUnmarkedMediaItems(offset: Int, limit: Int): List<MediaItem>
}
