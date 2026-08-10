package com.wardrobe.app.core.image.presentation.di

import com.wardrobe.app.core.image.presentation.DefaultGarmentPresentationEnhancer
import com.wardrobe.app.core.image.presentation.GarmentPresentationEnhancer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** [GarmentPresentationEnhancer] is deliberately on-device only (no cloud
 * variant — see its own KDoc), so unlike extraction/reconstruction/metadata
 * this is its only binding; there is no Router in `core:data` for it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GarmentPresentationEnhancerModule {
    @Binds
    abstract fun bindGarmentPresentationEnhancer(impl: DefaultGarmentPresentationEnhancer): GarmentPresentationEnhancer
}
