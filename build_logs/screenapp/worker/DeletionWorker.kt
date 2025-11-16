package ro.snapify.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ro.snapify.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DeletionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MediaRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val mediaItemId = inputData.getLong("media_item_id", -1L)
        if (mediaItemId == -1L) return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val mediaItem = repository.getById(mediaItemId)
                mediaItem?.let {
                    val file = File(it.filePath)
                    if (file.exists() && file.delete()) {
                        repository.deleteById(mediaItemId)
                    } else {
                        // Failed to delete file, but remove from DB anyway?
                        repository.deleteById(mediaItemId)
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
