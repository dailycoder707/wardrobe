package com.wardrobe.app.core.data.di

import com.wardrobe.app.core.ai.tryon.VirtualTryOnEngine
import com.wardrobe.app.core.data.ai.GarmentExtractionEngineRouter
import com.wardrobe.app.core.data.ai.GarmentMetadataEngineRouter
import com.wardrobe.app.core.data.ai.GarmentReconstructionEngineRouter
import com.wardrobe.app.core.data.ai.TryOnRouter
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wires Add-to-Wardrobe v2's capability routers (ADR-012 §11) as the actual
 * implementations everything above this layer sees — `GarmentImagePipeline`
 * (`core:image`) and `feature:capture`'s review screen depend only on
 * [GarmentExtractionEngine]/[GarmentReconstructionEngine]/
 * [GarmentMetadataEngine], never on whether a given call actually ran
 * on-device or in the cloud. [VirtualTryOnEngine] (M12) follows the same
 * contract; `StylingEngineRouter` binds to the pre-existing
 * `StylingEngineRepository` interface instead, in `RepositoryModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiProviderModule {
    @Binds
    abstract fun bindGarmentExtractionEngine(router: GarmentExtractionEngineRouter): GarmentExtractionEngine

    @Binds
    abstract fun bindGarmentReconstructionEngine(router: GarmentReconstructionEngineRouter): GarmentReconstructionEngine

    @Binds
    abstract fun bindGarmentMetadataEngine(router: GarmentMetadataEngineRouter): GarmentMetadataEngine

    @Binds
    abstract fun bindVirtualTryOnEngine(router: TryOnRouter): VirtualTryOnEngine
}
