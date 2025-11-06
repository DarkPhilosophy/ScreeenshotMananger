package com.araara.screenapp.di

import android.content.Context
import com.araara.screenapp.data.dao.ScreenshotDao
import com.araara.screenapp.data.database.ScreenshotDatabase
import com.araara.screenapp.data.repository.ScreenshotRepository
import com.araara.screenapp.data.repository.ScreenshotRepositoryImpl
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
