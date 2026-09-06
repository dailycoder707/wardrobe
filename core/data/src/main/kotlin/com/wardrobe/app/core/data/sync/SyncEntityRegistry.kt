package com.wardrobe.app.core.data.sync

import com.wardrobe.app.core.data.sync.handlers.BodyProfileSyncHandler
import com.wardrobe.app.core.data.sync.handlers.BrandSyncHandler
import com.wardrobe.app.core.data.sync.handlers.CategorySyncHandler
import com.wardrobe.app.core.data.sync.handlers.ColorSyncHandler
import com.wardrobe.app.core.data.sync.handlers.FabricSyncHandler
import com.wardrobe.app.core.data.sync.handlers.FeedbackSyncHandler
import com.wardrobe.app.core.data.sync.handlers.GarmentMaskSyncHandler
import com.wardrobe.app.core.data.sync.handlers.GarmentPlacementTemplateSyncHandler
import com.wardrobe.app.core.data.sync.handlers.GarmentSyncHandler
import com.wardrobe.app.core.data.sync.handlers.ImageMetadataSyncHandler
import com.wardrobe.app.core.data.sync.handlers.MaterialSyncHandler
import com.wardrobe.app.core.data.sync.handlers.OccasionSyncHandler
import com.wardrobe.app.core.data.sync.handlers.OutfitSyncHandler
import com.wardrobe.app.core.data.sync.handlers.PackingListItemSyncHandler
import com.wardrobe.app.core.data.sync.handlers.StyleRuleSyncHandler
import com.wardrobe.app.core.data.sync.handlers.TagSyncHandler
import com.wardrobe.app.core.data.sync.handlers.TripActivitySyncHandler
import com.wardrobe.app.core.data.sync.handlers.TripSyncHandler
import com.wardrobe.app.core.data.sync.handlers.WearEventSyncHandler
import com.wardrobe.app.core.data.sync.handlers.WishlistItemSyncHandler
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.FabricDao
import com.wardrobe.app.core.database.dao.FeedbackDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentMaskDao
import com.wardrobe.app.core.database.dao.GarmentPlacementTemplateDao
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.dao.StyleRuleDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.dao.TripDao
import com.wardrobe.app.core.database.dao.WearEventDao
import com.wardrobe.app.core.database.dao.WishlistDao
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import javax.inject.Inject
import javax.inject.Singleton

/** The six taxonomy-table DAOs a [SyncEntityHandler] needs, split out from
 * [WardrobeSyncDaos] purely to keep [SyncEntityRegistry]'s own constructor
 * under detekt's `LongParameterList` threshold — the same "bag of
 * repositories" pattern this codebase already uses
 * (`DeveloperPanelRepositories`, `StylingContextDependencies`, Phase 7). */
class TaxonomyDaos
    @Inject
    constructor(
        val categoryDao: CategoryDao,
        val colorDao: ColorDao,
        val materialDao: MaterialDao,
        val brandDao: BrandDao,
        val tagDao: TagDao,
        val occasionDao: OccasionDao,
        val fabricDao: FabricDao,
    )

/** The remaining garment-and-beyond DAOs a [SyncEntityHandler] needs — see
 * [TaxonomyDaos]'s KDoc for why this is split into two bags. */
class WardrobeSyncDaos
    @Inject
    constructor(
        val garmentDao: GarmentDao,
        val outfitDao: OutfitDao,
        val wearEventDao: WearEventDao,
        val styleRuleDao: StyleRuleDao,
        val feedbackDao: FeedbackDao,
        val tripDao: TripDao,
        val wishlistDao: WishlistDao,
        val imageMetadataDao: ImageMetadataDao,
    )

/** Phase 10's Personal Virtual Try-On DAOs/store — split from
 * [WardrobeSyncDaos] for the same `LongParameterList`-avoidance reason. */
class TryOnSyncDaos
    @Inject
    constructor(
        val bodyProfileDao: BodyProfileDao,
        val garmentPlacementTemplateDao: GarmentPlacementTemplateDao,
        val garmentMaskDao: GarmentMaskDao,
        val bodyImageFileStore: BodyImageFileStore,
    )

/**
 * Every syncable table's handler, in dependency order (Phase 8) — taxonomy
 * tables first (garments/outfits reference them), then garments, then
 * everything that references garments/outfits, so an incoming batch applies
 * cleanly in one pass whenever possible (a handler that can't yet resolve a
 * forward reference defers via [ApplyOutcome.LocalNewerIgnored] and retries
 * on the next sync regardless — order is an optimization, not a
 * correctness requirement). `image_metadata` is deliberately last: see
 * [ImageMetadataSyncHandler]'s KDoc for why its bytes must already be on
 * disk by the time its row is applied.
 */
@Singleton
class SyncEntityRegistry
    @Inject
    constructor(
        taxonomy: TaxonomyDaos,
        wardrobe: WardrobeSyncDaos,
        tryOn: TryOnSyncDaos,
        imageFileStore: ImageFileStore,
    ) {
        val handlersInOrder: List<SyncEntityHandler> =
            listOf(
                CategorySyncHandler(taxonomy.categoryDao),
                ColorSyncHandler(taxonomy.colorDao),
                MaterialSyncHandler(taxonomy.materialDao),
                FabricSyncHandler(taxonomy.fabricDao),
                BrandSyncHandler(taxonomy.brandDao),
                TagSyncHandler(taxonomy.tagDao),
                OccasionSyncHandler(taxonomy.occasionDao),
                GarmentSyncHandler(
                    wardrobe.garmentDao,
                    taxonomy.categoryDao,
                    taxonomy.colorDao,
                    taxonomy.brandDao,
                    taxonomy.materialDao,
                    taxonomy.tagDao,
                    taxonomy.fabricDao,
                    taxonomy.occasionDao,
                ),
                OutfitSyncHandler(wardrobe.outfitDao, taxonomy.occasionDao, wardrobe.garmentDao, taxonomy.tagDao),
                WearEventSyncHandler(
                    wardrobe.wearEventDao,
                    wardrobe.garmentDao,
                    wardrobe.outfitDao,
                    taxonomy.occasionDao,
                ),
                StyleRuleSyncHandler(wardrobe.styleRuleDao, wardrobe.feedbackDao),
                FeedbackSyncHandler(
                    wardrobe.feedbackDao,
                    wardrobe.garmentDao,
                    wardrobe.outfitDao,
                    wardrobe.styleRuleDao,
                ),
                TripSyncHandler(wardrobe.tripDao),
                TripActivitySyncHandler(wardrobe.tripDao),
                PackingListItemSyncHandler(wardrobe.tripDao, wardrobe.garmentDao),
                WishlistItemSyncHandler(wardrobe.wishlistDao, taxonomy.categoryDao, taxonomy.brandDao),
                ImageMetadataSyncHandler(wardrobe.imageMetadataDao, wardrobe.garmentDao, imageFileStore),
                BodyProfileSyncHandler(tryOn.bodyProfileDao, tryOn.bodyImageFileStore, imageFileStore),
                GarmentPlacementTemplateSyncHandler(
                    tryOn.garmentPlacementTemplateDao,
                    tryOn.bodyProfileDao,
                    wardrobe.garmentDao,
                ),
                GarmentMaskSyncHandler(
                    tryOn.garmentMaskDao,
                    tryOn.bodyProfileDao,
                    wardrobe.garmentDao,
                    tryOn.bodyImageFileStore,
                    imageFileStore,
                ),
            )

        private val byTableName: Map<String, SyncEntityHandler> = handlersInOrder.associateBy { it.tableName }

        fun handlerFor(tableName: String): SyncEntityHandler? = byTableName[tableName]

        /** Resolves a foreign-key reference (another table's syncId) to this
         * device's own local row id — passed into every handler's
         * `applyUpsert`. Deliberately a plain lookup table rather than
         * asking each handler to expose a second "resolve by syncId"
         * method: the two DAO bags above already have exactly this. */
        val resolver =
            SyncIdResolver { tableName, syncId -> localIdLookups[tableName]?.invoke(syncId) }

        private val localIdLookups: Map<String, suspend (String) -> Long?> =
            mapOf(
                "categories" to { syncId: String -> taxonomy.categoryDao.getBySyncId(syncId)?.id },
                "colors" to { syncId: String -> taxonomy.colorDao.getBySyncId(syncId)?.id },
                "materials" to { syncId: String -> taxonomy.materialDao.getBySyncId(syncId)?.id },
                "fabrics" to { syncId: String -> taxonomy.fabricDao.getBySyncId(syncId)?.id },
                "brands" to { syncId: String -> taxonomy.brandDao.getBySyncId(syncId)?.id },
                "tags" to { syncId: String -> taxonomy.tagDao.getBySyncId(syncId)?.id },
                "occasions" to { syncId: String -> taxonomy.occasionDao.getBySyncId(syncId)?.id },
                "garments" to { syncId: String -> wardrobe.garmentDao.getBySyncId(syncId)?.id },
                "outfits" to { syncId: String -> wardrobe.outfitDao.getBySyncId(syncId)?.id },
                "wear_events" to { syncId: String -> wardrobe.wearEventDao.getBySyncId(syncId)?.id },
                "style_rules" to { syncId: String -> wardrobe.styleRuleDao.getBySyncId(syncId)?.id },
                "feedback" to { syncId: String -> wardrobe.feedbackDao.getBySyncId(syncId)?.id },
                "trips" to { syncId: String -> wardrobe.tripDao.getBySyncId(syncId)?.id },
                "trip_activities" to { syncId: String -> wardrobe.tripDao.getActivityBySyncId(syncId)?.id },
                "packing_list_items" to { syncId: String -> wardrobe.tripDao.getPackingListItemBySyncId(syncId)?.id },
                "wishlist_items" to { syncId: String -> wardrobe.wishlistDao.getBySyncId(syncId)?.id },
                "image_metadata" to { syncId: String -> wardrobe.imageMetadataDao.getBySyncId(syncId)?.id },
                "body_profiles" to { syncId: String -> tryOn.bodyProfileDao.getProfileBySyncId(syncId)?.id },
            )
    }
