package com.wardrobe.app.core.image.segmentation.di

import com.wardrobe.app.core.image.segmentation.MlKitPersonRegionMasker
import com.wardrobe.app.core.image.segmentation.PersonRegionMasker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonRegionMaskerModule {
    @Binds
    abstract fun bindPersonRegionMasker(impl: MlKitPersonRegionMasker): PersonRegionMasker
}
