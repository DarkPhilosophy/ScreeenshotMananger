package com.ko.app.di

import android.content.Context
import com.ko.app.data.dao.ScreenshotDao
import com.ko.app.data.database.ScreenshotDatabase
import com.ko.app.data.repository.ScreenshotRepository
import com.ko.app.data.repository.ScreenshotRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideScreenshotDao(database: ScreenshotDatabase): ScreenshotDao {
        return database.screenshotDao()
    }

    @Provides
    @Singleton
    fun provideScreenshotRepository(dao: ScreenshotDao): ScreenshotRepository {
        return ScreenshotRepositoryImpl(dao)
    }

}
