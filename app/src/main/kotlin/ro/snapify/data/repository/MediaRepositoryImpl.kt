package ro.snapify.data.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import ro.snapify.data.dao.MediaDao
import ro.snapify.data.entity.MediaItem
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao
) : MediaRepository {

    override fun getAllMediaItems(): Flow<List<MediaItem>> = mediaDao.getAllMediaItems()

    override fun getAllMediaItemsLiveData(): LiveData<List<MediaItem>> =
        mediaDao.getAllMediaItemsLiveData()

    override fun getMarkedMediaItems(): Flow<List<MediaItem>> =
        mediaDao.getMarkedMediaItems()

    override fun getMarkedMediaItemsLiveData(): LiveData<List<MediaItem>> =
        mediaDao.getMarkedMediaItemsLiveData()

    override fun getKeptMediaItems(): Flow<List<MediaItem>> = mediaDao.getKeptMediaItems()

    override fun getUnmarkedMediaItems(): Flow<List<MediaItem>> =
        mediaDao.getUnmarkedMediaItems()

    override fun getMarkedCount(): Flow<Int> = mediaDao.getMarkedCount()

    override suspend fun insert(mediaItem: MediaItem): Long = mediaDao.insert(mediaItem)

    override suspend fun insertAll(mediaItems: List<MediaItem>): List<Long> =
        mediaDao.insertAll(mediaItems)

    override suspend fun update(mediaItem: MediaItem) = mediaDao.update(mediaItem)

    override suspend fun delete(mediaItem: MediaItem) = mediaDao.delete(mediaItem)

    override suspend fun getById(id: Long): MediaItem? = mediaDao.getById(id)

    override suspend fun getByFilePath(filePath: String): MediaItem? =
        mediaDao.getByFilePath(filePath)

    override suspend fun getExpiredMediaItems(currentTime: Long): List<MediaItem> =
        mediaDao.getExpiredMediaItems(currentTime)

    override suspend fun deleteById(id: Long) = mediaDao.deleteById(id)

    override suspend fun deleteByFilePath(filePath: String) =
        mediaDao.deleteByFilePath(filePath)

    override suspend fun markAsKept(id: Long) = mediaDao.markAsKept(id)

    override suspend fun markAsUnkept(id: Long) = mediaDao.markAsUnkept(id)

    override suspend fun markForDeletion(id: Long, deletionTime: Long) =
        mediaDao.markForDeletion(id, deletionTime)

    override suspend fun setDeletionWorkId(id: Long, workId: String?) =
        mediaDao.setDeletionWorkId(id, workId)

    override suspend fun deleteAll() = mediaDao.deleteAll()

    override suspend fun getPagedMediaItems(offset: Int, limit: Int): List<MediaItem> =
        mediaDao.getPagedMediaItems(offset, limit)

    override suspend fun getPagedMarkedMediaItems(offset: Int, limit: Int): List<MediaItem> =
        mediaDao.getPagedMarkedMediaItems(offset, limit)

    override suspend fun getPagedKeptMediaItems(offset: Int, limit: Int): List<MediaItem> =
        mediaDao.getPagedKeptMediaItems(offset, limit)

    override suspend fun getPagedUnmarkedMediaItems(offset: Int, limit: Int): List<MediaItem> =
        mediaDao.getPagedUnmarkedMediaItems(offset, limit)
}
