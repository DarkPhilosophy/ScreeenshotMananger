package ro.snapify.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import ro.snapify.ui.RefreshReason
import javax.inject.Singleton

// DI module intentionally removed from :core to keep Hilt configuration in the app module.

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRefreshFlow(): MutableSharedFlow<RefreshReason> = MutableSharedFlow(replay = 1)
}
