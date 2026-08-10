package com.wardrobe.app.core.ai.di

import com.wardrobe.app.core.ai.metrics.AiMetrics
import com.wardrobe.app.core.ai.metrics.RoomAiMetricsRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiCoreBindsModule {
    @Binds
    abstract fun bindAiMetrics(impl: RoomAiMetricsRecorder): AiMetrics
}
