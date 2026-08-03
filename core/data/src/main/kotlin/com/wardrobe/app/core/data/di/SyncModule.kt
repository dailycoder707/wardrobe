package com.wardrobe.app.core.data.di

import android.content.Context
import com.wardrobe.app.core.sync.crypto.AndroidKeystoreDeviceIdentity
import com.wardrobe.app.core.sync.crypto.DeviceIdentityKeyStore
import com.wardrobe.app.core.sync.discovery.DeviceDiscoveryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `core:sync` exposes plain classes, no Hilt annotations of its own — same
 * posture as `core:network` (Phase 7's `NetworkModule`). This module is
 * where those classes actually enter the app's dependency graph.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideDeviceIdentityKeyStore(): DeviceIdentityKeyStore = AndroidKeystoreDeviceIdentity()

    @Provides
    @Singleton
    fun provideDeviceDiscoveryService(
        @ApplicationContext context: Context,
    ): DeviceDiscoveryService = DeviceDiscoveryService(context)
}
