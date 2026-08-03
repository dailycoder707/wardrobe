package com.wardrobe.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wardrobe.app.core.database.converter.Converters
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
import com.wardrobe.app.core.database.entity.BodyMeasurementsEntity
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.BodyReferencePhotoEntity
import com.wardrobe.app.core.database.entity.BrandEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.FeedbackEntity
import com.wardrobe.app.core.database.entity.GarmentColorPaletteCrossRef
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentMaskEntity
import com.wardrobe.app.core.database.entity.GarmentMaterialCrossRef
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import com.wardrobe.app.core.database.entity.GarmentSeasonCrossRef
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import com.wardrobe.app.core.database.entity.ImportQueueItemEntity
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.database.entity.OccasionEntity
import com.wardrobe.app.core.database.entity.OutfitDressCodeCrossRef
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.OutfitSeasonCrossRef
import com.wardrobe.app.core.database.entity.OutfitTagCrossRef
import com.wardrobe.app.core.database.entity.PackingListItemEntity
import com.wardrobe.app.core.database.entity.PairedDeviceEntity
import com.wardrobe.app.core.database.entity.StatsCacheEntity
import com.wardrobe.app.core.database.entity.StyleProfileAvoidedCategoryCrossRef
import com.wardrobe.app.core.database.entity.StyleProfilePreferredBrandCrossRef
import com.wardrobe.app.core.database.entity.StyleRuleEntity
import com.wardrobe.app.core.database.entity.SyncChangeLogEntity
import com.wardrobe.app.core.database.entity.SyncConflictEntity
import com.wardrobe.app.core.database.entity.SyncHistoryEntity
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.database.entity.TripActivityEntity
import com.wardrobe.app.core.database.entity.TripEntity
import com.wardrobe.app.core.database.entity.WearEventEntity
import com.wardrobe.app.core.database.entity.WeatherCacheEntity
import com.wardrobe.app.core.database.entity.WishlistItemEntity
import com.wardrobe.app.core.model.garment.CategoryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** Every seeded row needs its own distinct sync identity (Phase 8) — the
 * unique index on `syncId` would otherwise reject the second seeded row. */
private fun newSyncId(): String = UUID.randomUUID().toString()

/**
 * Version 7 — the Add-to-Wardrobe ingestion fix. `MIGRATION_6_7` adds
 * `garments.notes`, ~37 new [CategoryEntity] subcategory rows under the
 * existing 13 top-level buckets, and `import_queue_items` (the Room-backed,
 * device-local-only Add-to-Wardrobe import queue). `MIGRATION_5_6`
 * (Phase 10) adds five new tables for Personal Virtual Try-On
 * (`body_profiles`, `body_reference_photos`, `body_measurements`,
 * `garment_placement_templates`, `garment_masks`); see
 * `phase-10-personal-virtual-tryon.md`. `MIGRATION_4_5` (Phase 8) adds a
 * `syncId`/`updatedAt` pair to every independently sync-tracked table plus
 * four sync-only tables (`sync_change_log`, `paired_device`,
 * `sync_conflict`, `sync_history`). `MIGRATION_3_4` covers v3→v4 (Phase 7
 * weather columns), `MIGRATION_2_3` covers v2→v3 (Phase 6's
 * `garments.isInLaundry`), `MIGRATION_1_2` covers v1→v2 from Phase 5d.
 * `fallbackToDestructiveMigration` must never be used from version 2 onward, see
 * phase-3-persistence.md's migration strategy. The actual
 * `Room.databaseBuilder(...)` construction and Hilt `@Provides` wiring belong to
 * `core:data`'s DI module (Phase 5a), not here — this class only declares the
 * schema.
 */
@Database(
    entities = [
        CategoryEntity::class,
        ColorEntity::class,
        MaterialEntity::class,
        BrandEntity::class,
        TagEntity::class,
        OccasionEntity::class,
        GarmentEntity::class,
        GarmentColorPaletteCrossRef::class,
        GarmentMaterialCrossRef::class,
        GarmentTagCrossRef::class,
        GarmentSeasonCrossRef::class,
        GarmentDressCodeCrossRef::class,
        OutfitEntity::class,
        OutfitGarmentCrossRef::class,
        OutfitSeasonCrossRef::class,
        OutfitDressCodeCrossRef::class,
        OutfitTagCrossRef::class,
        WearEventEntity::class,
        StyleRuleEntity::class,
        FeedbackEntity::class,
        StyleProfilePreferredBrandCrossRef::class,
        StyleProfileAvoidedCategoryCrossRef::class,
        TripEntity::class,
        TripActivityEntity::class,
        PackingListItemEntity::class,
        WishlistItemEntity::class,
        WeatherCacheEntity::class,
        ImageMetadataEntity::class,
        StatsCacheEntity::class,
        SyncChangeLogEntity::class,
        PairedDeviceEntity::class,
        SyncConflictEntity::class,
        SyncHistoryEntity::class,
        BodyProfileEntity::class,
        BodyReferencePhotoEntity::class,
        BodyMeasurementsEntity::class,
        GarmentPlacementTemplateEntity::class,
        GarmentMaskEntity::class,
        ImportQueueItemEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WardrobeDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun colorDao(): ColorDao

    abstract fun materialDao(): MaterialDao

    abstract fun brandDao(): BrandDao

    abstract fun tagDao(): TagDao

    abstract fun occasionDao(): OccasionDao

    abstract fun garmentDao(): GarmentDao

    abstract fun outfitDao(): OutfitDao

    abstract fun wearEventDao(): WearEventDao

    abstract fun styleRuleDao(): StyleRuleDao

    abstract fun feedbackDao(): FeedbackDao

    abstract fun styleProfileDao(): StyleProfileDao

    abstract fun tripDao(): TripDao

    abstract fun wishlistDao(): WishlistDao

    abstract fun weatherCacheDao(): WeatherCacheDao

    abstract fun imageMetadataDao(): ImageMetadataDao

    abstract fun statsDao(): StatsDao

    abstract fun syncChangeLogDao(): SyncChangeLogDao

    abstract fun pairedDeviceDao(): PairedDeviceDao

    abstract fun syncConflictDao(): SyncConflictDao

    abstract fun syncHistoryDao(): SyncHistoryDao

    abstract fun bodyProfileDao(): BodyProfileDao

    abstract fun garmentPlacementTemplateDao(): GarmentPlacementTemplateDao

    abstract fun garmentMaskDao(): GarmentMaskDao

    abstract fun importQueueDao(): ImportQueueDao

    /**
     * Seeds the fixed default [OccasionEntity] rows on first creation only (Phase 1
     * Section 9: Occasion is user-extensible, but ships with sensible defaults).
     * `databaseProvider` is a plain lambda, not `javax.inject.Provider` — this module
     * has no Hilt dependency; the caller (Phase 5a's DI module) supplies
     * `{ hiltProvidedDatabase }`.
     */
    class SeedCallback(
        private val scope: CoroutineScope,
        private val databaseProvider: () -> WardrobeDatabase,
    ) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch {
                val now = System.currentTimeMillis()
                val occasionDao = databaseProvider().occasionDao()
                occasionDao.insertAll(
                    DEFAULT_OCCASIONS.map { OccasionEntity(name = it, syncId = newSyncId(), updatedAt = now) },
                )
                seedDefaultCategories(databaseProvider().categoryDao(), now)
            }
        }

        /**
         * Seeds a starter [CategoryEntity] tree covering every item type the
         * Phase 6 styling engine needs to reason about (garments, footwear, bags,
         * jewelry, watches, and small accessories) — every row is a plain,
         * user-editable/deletable [CategoryEntity] like any other, per this
         * schema's free-form TOP/SUB taxonomy (phase-1-architecture.md Section
         * 9); this is starter data, not a hardcoded enum. The subcategory list
         * below must stay in sync with `Migration6To7`'s
         * `NEW_SUBCATEGORIES_BY_TOP_BUCKET`, since `onCreate` (this method) only
         * ever runs for a brand-new install and that migration only ever runs
         * for one upgrading from an older version — a fresh install would never
         * see the migration's inserts otherwise.
         */
        private suspend fun seedDefaultCategories(
            categoryDao: CategoryDao,
            now: Long,
        ) {
            val topLevelNames =
                listOf(
                    "Tops",
                    "Bottoms",
                    "Dresses",
                    "Jumpsuits",
                    "Outerwear",
                    "Shoes",
                    "Bags",
                    "Jewelry",
                    "Belts",
                    "Scarves",
                    "Hair Accessories",
                    "Sunglasses",
                    "Other Accessories",
                )
            val topLevelIds =
                categoryDao.insertAll(
                    topLevelNames.map {
                        CategoryEntity(
                            name = it,
                            parentId = null,
                            level = CategoryLevel.TOP,
                            syncId = newSyncId(),
                            updatedAt = now,
                        )
                    },
                )
            val topLevelIdByName = topLevelNames.zip(topLevelIds).toMap()
            seedSubcategories(categoryDao, topLevelIdByName, now)
        }

        private suspend fun seedSubcategories(
            categoryDao: CategoryDao,
            topLevelIdByName: Map<String, Long>,
            now: Long,
        ) {
            fun sub(
                name: String,
                topBucketName: String,
            ) = CategoryEntity(
                name = name,
                parentId = topLevelIdByName.getValue(topBucketName),
                level = CategoryLevel.SUB,
                syncId = newSyncId(),
                updatedAt = now,
            )
            val subcategoriesByTopBucket =
                mapOf(
                    "Shoes" to
                        listOf(
                            "Sandals",
                            "Boots",
                            "Sneakers",
                            "Running Shoes",
                            "Heels",
                            "Flats",
                            "Loafers",
                            "Slippers",
                        ),
                    "Jewelry" to
                        listOf("Watches", "Earrings", "Necklaces", "Bracelets", "Rings", "Nose Ring", "Anklet"),
                    "Tops" to listOf("T-Shirts", "Shirts", "Polo", "Tank Tops", "Hoodies", "Sweaters", "Kurtas"),
                    "Outerwear" to listOf("Jackets", "Blazers", "Coats"),
                    "Dresses" to listOf("Sarees"),
                    "Bottoms" to listOf("Jeans", "Pants", "Shorts", "Skirts", "Leggings"),
                    "Bags" to listOf("Backpack", "Handbag", "Tote", "Laptop Bag", "Sling Bag", "Wallet"),
                    "Other Accessories" to listOf("Cap", "Hat", "Gloves", "Socks", "Tie", "Bow Tie"),
                    "Hair Accessories" to listOf("Hair Band", "Hair Clip"),
                )
            categoryDao.insertAll(
                subcategoriesByTopBucket.flatMap { (topBucketName, subNames) ->
                    subNames.map { sub(it, topBucketName) }
                },
            )
        }

        private companion object {
            val DEFAULT_OCCASIONS =
                listOf(
                    "Casual",
                    "Work",
                    "Event",
                    "Formal",
                    "Athletic",
                    "Travel",
                    "Date Night",
                    "Loungewear",
                )
        }
    }

    /** Issues a full WAL checkpoint so the on-disk `.db` file alone (no `-wal`/`-shm`
     * sidecars) reflects every committed write — required before copying the file
     * for export, since Room runs in WAL mode by default. Deliberately not wrapped
     * in [androidx.room.withTransaction]: a checkpoint is not a data-modifying
     * transaction, and PRAGMA statements are not guaranteed to behave the same way
     * inside one. See phase-5a-data-layer.md's backup/restore design. */
    fun checkpoint() {
        openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
    }

    companion object {
        /** Shared by this class's own `Room.databaseBuilder` construction
         * (`core:data`'s `DatabaseModule`) and `BackupRepositoryImpl`, which needs
         * the same filename to locate the file on disk — one constant, not two
         * copies of the same string. */
        const val DATABASE_NAME = "wardrobe.db"
    }
}
