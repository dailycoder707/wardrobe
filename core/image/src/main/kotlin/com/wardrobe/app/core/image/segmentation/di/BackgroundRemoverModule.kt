package com.wardrobe.app.core.image.segmentation.di

import com.wardrobe.app.core.image.segmentation.BackgroundRemover
import com.wardrobe.app.core.image.segmentation.MlKitBackgroundRemover
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The one line that would change to swap background-removal implementations
 * (ADR-008) — see phase-5b-image-pipeline.md. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundRemoverModule {
    @Binds
    abstract fun bindBackgroundRemover(impl: MlKitBackgroundRemover): BackgroundRemover
}
