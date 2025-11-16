package ro.snapify.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ro.snapify.data.dao.MediaDao
import ro.snapify.data.database.ScreenshotDatabase
import ro.snapify.data.repository.MediaRepository
import ro.snapify.data.repository.MediaRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideScreenshotDatabase(@ApplicationContext context: Context): ScreenshotDatabase {
        return ScreenshotDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideMediaDao(database: ScreenshotDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun provideMediaRepository(dao: MediaDao): MediaRepository {
        return MediaRepositoryImpl(dao)
    }

}
