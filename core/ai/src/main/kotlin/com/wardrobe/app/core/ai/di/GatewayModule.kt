package com.wardrobe.app.core.ai.di

import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.DefaultAiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.privacy.DefaultPrivacyPreprocessor
import com.wardrobe.app.core.ai.privacy.FaceBlurrer
import com.wardrobe.app.core.ai.privacy.MlKitFaceBlurrer
import com.wardrobe.app.core.ai.privacy.PrivacyPreprocessor
import com.wardrobe.app.core.model.ai.AiVendor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * [Multibinds] declares these maps legally injectable *empty* until a real
 * vendor adapter contributes an `@IntoMap` entry (M6) — without this,
 * Dagger can't resolve `Map<AiVendor, VisionPromptAdapter>` at all before
 * any adapter exists, and [DefaultAiGateway] needs to compile and be
 * testable before M6 lands.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {
    @Binds
    abstract fun bindAiGateway(impl: DefaultAiGateway): AiGateway

    @Binds
    abstract fun bindPrivacyPreprocessor(impl: DefaultPrivacyPreprocessor): PrivacyPreprocessor

    @Binds
    abstract fun bindFaceBlurrer(impl: MlKitFaceBlurrer): FaceBlurrer

    @Multibinds
    abstract fun visionPromptAdapters(): Map<AiVendor, VisionPromptAdapter>

    @Multibinds
    abstract fun imageTaskAdapters(): Map<AiVendor, ImageTaskAdapter>
}
