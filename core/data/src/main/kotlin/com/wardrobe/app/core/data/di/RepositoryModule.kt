package com.wardrobe.app.core.data.di

import com.wardrobe.app.core.data.ai.AiProviderSettingsRepositoryImpl
import com.wardrobe.app.core.data.ai.StylingEngineRouter
import com.wardrobe.app.core.data.repository.BackupRepositoryImpl
import com.wardrobe.app.core.data.repository.BodyProfileRepositoryImpl
import com.wardrobe.app.core.data.repository.BrandRepositoryImpl
import com.wardrobe.app.core.data.repository.CategoryRepositoryImpl
import com.wardrobe.app.core.data.repository.ClosetPreferencesRepositoryImpl
import com.wardrobe.app.core.data.repository.ColorRepositoryImpl
import com.wardrobe.app.core.data.repository.FabricRepositoryImpl
import com.wardrobe.app.core.data.repository.GarmentMaskRepositoryImpl
import com.wardrobe.app.core.data.repository.GarmentRepositoryImpl
import com.wardrobe.app.core.data.repository.ImageRepositoryImpl
import com.wardrobe.app.core.data.repository.ImportQueueRepositoryImpl
import com.wardrobe.app.core.data.repository.MaterialRepositoryImpl
import com.wardrobe.app.core.data.repository.OccasionRepositoryImpl
import com.wardrobe.app.core.data.repository.OnboardingRepositoryImpl
import com.wardrobe.app.core.data.repository.OutfitRepositoryImpl
import com.wardrobe.app.core.data.repository.PersonalizationRepositoryImpl
import com.wardrobe.app.core.data.repository.StatsRepositoryImpl
import com.wardrobe.app.core.data.repository.StyleProfileRepositoryImpl
import com.wardrobe.app.core.data.repository.StyleRuleRepositoryImpl
import com.wardrobe.app.core.data.repository.StylistPreferencesRepositoryImpl
import com.wardrobe.app.core.data.repository.SyncPreferencesRepositoryImpl
import com.wardrobe.app.core.data.repository.TagRepositoryImpl
import com.wardrobe.app.core.data.repository.TripRepositoryImpl
import com.wardrobe.app.core.data.repository.TryOnPlacementRepositoryImpl
import com.wardrobe.app.core.data.repository.VirtualTryOnRenderRepositoryImpl
import com.wardrobe.app.core.data.repository.WardrobeIntelligenceRepositoryImpl
import com.wardrobe.app.core.data.repository.WearEventRepositoryImpl
import com.wardrobe.app.core.data.repository.WeatherPreferencesRepositoryImpl
import com.wardrobe.app.core.data.repository.WeatherRefreshSchedulerImpl
import com.wardrobe.app.core.data.repository.WeatherRepositoryImpl
import com.wardrobe.app.core.data.repository.WishlistRepositoryImpl
import com.wardrobe.app.core.data.sync.DevicePairingRepositoryImpl
import com.wardrobe.app.core.data.sync.SyncRepositoryImpl
import com.wardrobe.app.core.data.sync.SyncSchedulerImpl
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.domain.repository.BackupRepository
import com.wardrobe.app.core.domain.repository.BodyProfileRepository
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ClosetPreferencesRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.DevicePairingRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.GarmentMaskRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OnboardingRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StyleProfileRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.domain.repository.SyncPreferencesRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.domain.repository.SyncScheduler
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.TryOnPlacementRepository
import com.wardrobe.app.core.domain.repository.VirtualTryOnRenderRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRefreshScheduler
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.domain.repository.WishlistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every `core:domain` repository interface implemented so far to its
 * `core:data` class. `StylingEngineRepository` was added Phase 6.
 * `WeatherRepository`/`WeatherPreferencesRepository` were added Phase 7 — the
 * last two interfaces this app's domain layer declared without an
 * implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindColorRepository(impl: ColorRepositoryImpl): ColorRepository

    @Binds
    @Singleton
    abstract fun bindMaterialRepository(impl: MaterialRepositoryImpl): MaterialRepository

    @Binds
    @Singleton
    abstract fun bindFabricRepository(impl: FabricRepositoryImpl): FabricRepository

    @Binds
    @Singleton
    abstract fun bindBrandRepository(impl: BrandRepositoryImpl): BrandRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindOccasionRepository(impl: OccasionRepositoryImpl): OccasionRepository

    @Binds
    @Singleton
    abstract fun bindGarmentRepository(impl: GarmentRepositoryImpl): GarmentRepository

    @Binds
    @Singleton
    abstract fun bindOutfitRepository(impl: OutfitRepositoryImpl): OutfitRepository

    @Binds
    @Singleton
    abstract fun bindWearEventRepository(impl: WearEventRepositoryImpl): WearEventRepository

    @Binds
    @Singleton
    abstract fun bindStyleRuleRepository(impl: StyleRuleRepositoryImpl): StyleRuleRepository

    @Binds
    @Singleton
    abstract fun bindStyleProfileRepository(impl: StyleProfileRepositoryImpl): StyleProfileRepository

    @Binds
    @Singleton
    abstract fun bindPersonalizationRepository(impl: PersonalizationRepositoryImpl): PersonalizationRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindClosetPreferencesRepository(impl: ClosetPreferencesRepositoryImpl): ClosetPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindStylistPreferencesRepository(impl: StylistPreferencesRepositoryImpl): StylistPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindStylingEngineRepository(impl: StylingEngineRouter): StylingEngineRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindWeatherPreferencesRepository(impl: WeatherPreferencesRepositoryImpl): WeatherPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRefreshScheduler(impl: WeatherRefreshSchedulerImpl): WeatherRefreshScheduler

    @Binds
    @Singleton
    abstract fun bindDevicePairingRepository(impl: DevicePairingRepositoryImpl): DevicePairingRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindSyncPreferencesRepository(impl: SyncPreferencesRepositoryImpl): SyncPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: SyncSchedulerImpl): SyncScheduler

    @Binds
    @Singleton
    abstract fun bindWardrobeIntelligenceRepository(
        impl: WardrobeIntelligenceRepositoryImpl,
    ): WardrobeIntelligenceRepository

    @Binds
    @Singleton
    abstract fun bindBodyProfileRepository(impl: BodyProfileRepositoryImpl): BodyProfileRepository

    @Binds
    @Singleton
    abstract fun bindTryOnPlacementRepository(impl: TryOnPlacementRepositoryImpl): TryOnPlacementRepository

    @Binds
    @Singleton
    abstract fun bindVirtualTryOnRenderRepository(impl: VirtualTryOnRenderRepositoryImpl): VirtualTryOnRenderRepository

    @Binds
    @Singleton
    abstract fun bindGarmentMaskRepository(impl: GarmentMaskRepositoryImpl): GarmentMaskRepository

    @Binds
    @Singleton
    abstract fun bindImportQueueRepository(impl: ImportQueueRepositoryImpl): ImportQueueRepository

    @Binds
    @Singleton
    abstract fun bindAiProviderSettingsRepository(impl: AiProviderSettingsRepositoryImpl): AiProviderSettingsRepository
}
