package com.wardrobe.app.core.data.di

import android.content.Context
import androidx.room.Room
import com.wardrobe.app.core.database.WardrobeDatabase
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.FeedbackDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentMaskDao
import com.wardrobe.app.core.database.dao.GarmentPlacementTemplateDao
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.database.dao.ImportQueueDao
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.dao.PairedDeviceDao
import com.wardrobe.app.core.database.dao.StatsDao
import com.wardrobe.app.core.database.dao.StyleProfileDao
import com.wardrobe.app.core.database.dao.StyleRuleDao
import com.wardrobe.app.core.database.dao.SyncChangeLogDao
import com.wardrobe.app.core.database.dao.SyncConflictDao
import com.wardrobe.app.core.database.dao.SyncHistoryDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.dao.TripDao
import com.wardrobe.app.core.database.dao.WearEventDao
import com.wardrobe.app.core.database.dao.WeatherCacheDao
import com.wardrobe.app.core.database.dao.WishlistDao
import com.wardrobe.app.core.database.migration.MIGRATION_1_2
import com.wardrobe.app.core.database.migration.MIGRATION_2_3
import com.wardrobe.app.core.database.migration.MIGRATION_3_4
import com.wardrobe.app.core.database.migration.MIGRATION_4_5
import com.wardrobe.app.core.database.migration.MIGRATION_5_6
import com.wardrobe.app.core.database.migration.MIGRATION_6_7
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * A dedicated, app-lifetime scope for [WardrobeDatabase.SeedCallback] — Phase
     * 3's callback needed one and had no caller yet; this is that caller. Not
     * shared with `core:datastore`'s own internal scope (that one is DataStore's
     * own recommendation to keep private to its factory), since seeding and
     * preferences persistence are unrelated lifecycles that happen to both be
     * "as long as the app runs."
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideWardrobeDatabase(
        @ApplicationContext context: Context,
        applicationScope: CoroutineScope,
        databaseProvider: Provider<WardrobeDatabase>,
    ): WardrobeDatabase =
        Room
            .databaseBuilder(context, WardrobeDatabase::class.java, WardrobeDatabase.DATABASE_NAME)
            .addCallback(WardrobeDatabase.SeedCallback(applicationScope) { databaseProvider.get() })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideCategoryDao(db: WardrobeDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideColorDao(db: WardrobeDatabase): ColorDao = db.colorDao()

    @Provides
    fun provideMaterialDao(db: WardrobeDatabase): MaterialDao = db.materialDao()

    @Provides
    fun provideBrandDao(db: WardrobeDatabase): BrandDao = db.brandDao()

    @Provides
    fun provideTagDao(db: WardrobeDatabase): TagDao = db.tagDao()

    @Provides
    fun provideOccasionDao(db: WardrobeDatabase): OccasionDao = db.occasionDao()

    @Provides
    fun provideGarmentDao(db: WardrobeDatabase): GarmentDao = db.garmentDao()

    @Provides
    fun provideOutfitDao(db: WardrobeDatabase): OutfitDao = db.outfitDao()

    @Provides
    fun provideWearEventDao(db: WardrobeDatabase): WearEventDao = db.wearEventDao()

    @Provides
    fun provideStyleRuleDao(db: WardrobeDatabase): StyleRuleDao = db.styleRuleDao()

    @Provides
    fun provideFeedbackDao(db: WardrobeDatabase): FeedbackDao = db.feedbackDao()

    @Provides
    fun provideStyleProfileDao(db: WardrobeDatabase): StyleProfileDao = db.styleProfileDao()

    @Provides
    fun provideTripDao(db: WardrobeDatabase): TripDao = db.tripDao()

    @Provides
    fun provideWishlistDao(db: WardrobeDatabase): WishlistDao = db.wishlistDao()

    @Provides
    fun provideWeatherCacheDao(db: WardrobeDatabase): WeatherCacheDao = db.weatherCacheDao()

    @Provides
    fun provideImageMetadataDao(db: WardrobeDatabase): ImageMetadataDao = db.imageMetadataDao()

    @Provides
    fun provideStatsDao(db: WardrobeDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideSyncChangeLogDao(db: WardrobeDatabase): SyncChangeLogDao = db.syncChangeLogDao()

    @Provides
    fun providePairedDeviceDao(db: WardrobeDatabase): PairedDeviceDao = db.pairedDeviceDao()

    @Provides
    fun provideSyncConflictDao(db: WardrobeDatabase): SyncConflictDao = db.syncConflictDao()

    @Provides
    fun provideSyncHistoryDao(db: WardrobeDatabase): SyncHistoryDao = db.syncHistoryDao()

    @Provides
    fun provideBodyProfileDao(db: WardrobeDatabase): BodyProfileDao = db.bodyProfileDao()

    @Provides
    fun provideGarmentPlacementTemplateDao(db: WardrobeDatabase): GarmentPlacementTemplateDao =
        db.garmentPlacementTemplateDao()

    @Provides
    fun provideGarmentMaskDao(db: WardrobeDatabase): GarmentMaskDao = db.garmentMaskDao()

    @Provides
    fun provideImportQueueDao(db: WardrobeDatabase): ImportQueueDao = db.importQueueDao()
}
