package com.araara.screenapp.di

import com.araara.screenapp.data.preferences.AppPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun preferences(): AppPreferences
}
