package ro.snapify.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ro.snapify.data.entity.MediaItem

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mediaItem: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaItems: List<MediaItem>): List<Long>

    @Update
    suspend fun update(mediaItem: MediaItem)

    @Delete
    suspend fun delete(mediaItem: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_items WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE deletionTimestamp < :currentTime AND isKept = 0")
    suspend fun getExpiredMediaItems(currentTime: Long): List<MediaItem>

    @Query("UPDATE media_items SET isKept = 1, deletionTimestamp = NULL, deletionWorkId = NULL WHERE id = :id")
    suspend fun markAsKept(id: Long)

    @Query("UPDATE media_items SET isKept = 0, deletionTimestamp = NULL, deletionWorkId = NULL WHERE id = :id")
    suspend fun markAsUnkept(id: Long)

    @Query("UPDATE media_items SET deletionTimestamp = :deletionTime WHERE id = :id")
    suspend fun markForDeletion(id: Long, deletionTime: Long)

    @Query("UPDATE media_items SET deletionWorkId = :workId WHERE id = :id")
    suspend fun setDeletionWorkId(id: Long, workId: String?)

    @Query("SELECT * FROM media_items")
    fun getAllMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items")
    fun getAllMediaItemsLiveData(): LiveData<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getMarkedMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getMarkedMediaItemsLiveData(): LiveData<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isKept = 1 ORDER BY createdAt DESC")
    fun getKeptMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE deletionTimestamp IS NULL AND isKept = 0 ORDER BY createdAt DESC")
    fun getUnmarkedMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT COUNT(*) FROM media_items WHERE deletionTimestamp IS NOT NULL AND isKept = 0")
    fun getMarkedCount(): Flow<Int>

    @Query("SELECT * FROM media_items ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMediaItems(offset: Int, limit: Int): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE deletionTimestamp IS NOT NULL AND isKept = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMarkedMediaItems(offset: Int, limit: Int): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE isKept = 1 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedKeptMediaItems(offset: Int, limit: Int): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE deletionTimestamp IS NULL AND isKept = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedUnmarkedMediaItems(offset: Int, limit: Int): List<MediaItem>
}
