package com.wardrobe.app.core.tryon.pose.di

import com.wardrobe.app.core.tryon.pose.BodyAnchorEstimator
import com.wardrobe.app.core.tryon.pose.MlKitBodyAnchorEstimator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The one line that would change to swap body-landmark estimation
 * implementations — see [com.wardrobe.app.core.image.segmentation.di.BackgroundRemoverModule],
 * whose exact shape this mirrors. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BodyAnchorEstimatorModule {
    @Binds
    abstract fun bindBodyAnchorEstimator(impl: MlKitBodyAnchorEstimator): BodyAnchorEstimator
}
