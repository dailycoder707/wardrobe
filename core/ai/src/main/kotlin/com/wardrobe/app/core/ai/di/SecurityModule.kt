package com.wardrobe.app.core.ai.di

import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.ai.security.EncryptedApiKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindApiKeyStore(impl: EncryptedApiKeyStore): ApiKeyStore
}
