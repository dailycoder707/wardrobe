package com.wardrobe.app.di

import com.wardrobe.app.BuildConfig
import com.wardrobe.app.feature.settings.di.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The one module that can bind a real `BuildConfig.VERSION_NAME` — only
 * `app`, the sole `com.android.application` module, has that field. Feeds
 * `feature:settings`'s Profile screen ("About" section) without that module
 * ever depending on `app`. */
@Module
@InstallIn(SingletonComponent::class)
object AppVersionModule {
    @Provides
    @AppVersion
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME
}
