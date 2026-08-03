package com.wardrobe.app.core.datastore.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private const val PREFERENCES_FILE_NAME = "wardrobe_preferences"

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): androidx.datastore.core.DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A dedicated scope, not the caller's — DataStore's own recommendation.
            // This file is small and single-user; a SupervisorJob on Dispatchers.IO
            // is sufficient without needing to share the app's broader application
            // scope (defined separately in core:data/di for Room's seed callback).
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
}
