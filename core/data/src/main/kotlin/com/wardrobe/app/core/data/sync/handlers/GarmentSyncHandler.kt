package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentWithRelations
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.entity.GarmentColorPaletteCrossRef
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentMaterialCrossRef
import com.wardrobe.app.core.database.entity.GarmentSeasonCrossRef
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
import kotlinx.serialization.Serializable

@Serializable
private data class PaletteWire(
    val colorSyncId: String,
    val weightPercent: Int,
)

@Serializable
private data class MaterialWireEntry(
    val materialSyncId: String,
    val percentage: Int?,
)

@Serializable
private data class GarmentWire(
    val name: String?,
    val categorySyncId: String,
    val primaryColorSyncId: String?,
    val pattern: String?,
    val fit: Fit?,
    val length: GarmentLength?,
    val sleeveLength: SleeveLength?,
    val warmthRating: Int?,
    val breathabilityRating: Int?,
    val brandSyncId: String?,
    val size: String?,
    val price: Double?,
    val currencyCode: String?,
    val purchaseDate: String?,
    val condition: Condition?,
    val careNotes: String?,
    val notes: String?,
    val status: GarmentStatus,
    val isReviewed: Boolean,
    val isFavorite: Boolean,
    val isInLaundry: Boolean,
    val searchText: String,
    val createdAt: Long,
    val palette: List<PaletteWire>,
    val materials: List<MaterialWireEntry>,
    val tagSyncIds: List<String>,
    val seasons: List<Season>,
    val dressCodes: List<DressCode>,
)

/**
 * The most complex handler in this phase: a garment's own scalar fields
 * follow ordinary "newest wins" (LWW), but its five collections (palette,
 * materials, tags, seasons, dress codes) always *merge* — set union keyed
 * by the referenced row's syncId — regardless of which side's scalar
 * fields won, per the brief's "Collections: Merge" rule being independent
 * of "Simple fields: newest wins." See `phase-8-multi-device-sync.md`'s
 * "Conflict resolution" section.
 *
 * Known limitation, stated rather than hidden: union-only merging means a
 * tag/season/dress-code *removed* on one device can reappear if the other
 * device still has it at the next sync — true remove-tombstone semantics
 * per collection entry would need far more state than this phase's outbox
 * design tracks, for a low-stakes edge case on a personal wardrobe.
 */
class GarmentSyncHandler(
    private val garmentDao: GarmentDao,
    private val categoryDao: CategoryDao,
    private val colorDao: ColorDao,
    private val brandDao: BrandDao,
    private val materialDao: MaterialDao,
    private val tagDao: TagDao,
) : SyncEntityHandler {
    override val tableName = "garments"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val relations = garmentDao.getWithRelationsBySyncId(syncId) ?: return null
        val garment = relations.garment
        val categorySyncId = categoryDao.getById(garment.categoryId)?.syncId
        return if (categorySyncId == null) null else buildGarmentWireJson(garment, categorySyncId, relations)
    }

    private suspend fun buildGarmentWireJson(
        garment: GarmentEntity,
        categorySyncId: String,
        relations: GarmentWithRelations,
    ): String {
        val brandSyncId = garment.brandId?.let { brandDao.getById(it)?.syncId }
        val primaryColorSyncId = garment.primaryColorId?.let { colorDao.getById(it)?.syncId }
        val wire =
            GarmentWire(
                name = garment.name,
                categorySyncId = categorySyncId,
                primaryColorSyncId = primaryColorSyncId,
                pattern = garment.pattern,
                fit = garment.fit,
                length = garment.length,
                sleeveLength = garment.sleeveLength,
                warmthRating = garment.warmthRating,
                breathabilityRating = garment.breathabilityRating,
                brandSyncId = brandSyncId,
                size = garment.size,
                price = garment.price,
                currencyCode = garment.currencyCode,
                purchaseDate = garment.purchaseDate,
                condition = garment.condition,
                careNotes = garment.careNotes,
                notes = garment.notes,
                status = garment.status,
                isReviewed = garment.isReviewed,
                isFavorite = garment.isFavorite,
                isInLaundry = garment.isInLaundry,
                searchText = garment.searchText,
                createdAt = garment.createdAt,
                palette =
                    relations.palette.mapNotNull { ref ->
                        colorDao.getById(ref.colorId)?.syncId?.let { PaletteWire(it, ref.weightPercent) }
                    },
                materials =
                    relations.materials.mapNotNull { ref ->
                        materialDao.getById(ref.materialId)?.syncId?.let { MaterialWireEntry(it, ref.percentage) }
                    },
                tagSyncIds = relations.tags.mapNotNull { tagDao.getById(it.tagId)?.syncId },
                seasons = relations.seasons.map { it.season },
                dressCodes = relations.dressCodes.map { it.dressCode },
            )
        return syncJson.encodeToString(GarmentWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(GarmentWire.serializer(), fieldsJson)
        val existing = garmentDao.getBySyncId(syncId)
        // The category this garment belongs to may not have arrived yet in
        // this batch — deferred by treating it as not-yet-applicable rather
        // than crashing or dropping the row; a future sync (after the
        // category exists locally) retries it, since the change log entry on
        // the sender only advances once this device acks it.
        val categoryId = resolver.resolveLocalId("categories", wire.categorySyncId)
        if (categoryId != null) {
            val scalarOutcome = applyScalarFields(existing, wire, syncId, categoryId, remoteUpdatedAt, resolver)
            val garmentLocalId = existing?.id ?: garmentDao.getBySyncId(syncId)?.id
            if (garmentLocalId != null) mergeCollections(garmentLocalId, wire, resolver)
            return scalarOutcome
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    private suspend fun applyScalarFields(
        existing: GarmentEntity?,
        wire: GarmentWire,
        syncId: String,
        categoryId: Long,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        if (existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val brandId = wire.brandSyncId?.let { resolver.resolveLocalId("brands", it) }
        val primaryColorId = wire.primaryColorSyncId?.let { resolver.resolveLocalId("colors", it) }
        val entity =
            GarmentEntity(
                id = existing?.id ?: 0,
                name = wire.name,
                categoryId = categoryId,
                primaryColorId = primaryColorId,
                pattern = wire.pattern,
                fit = wire.fit,
                length = wire.length,
                sleeveLength = wire.sleeveLength,
                warmthRating = wire.warmthRating,
                breathabilityRating = wire.breathabilityRating,
                brandId = brandId,
                size = wire.size,
                price = wire.price,
                currencyCode = wire.currencyCode,
                purchaseDate = wire.purchaseDate,
                condition = wire.condition,
                careNotes = wire.careNotes,
                notes = wire.notes,
                status = wire.status,
                isReviewed = wire.isReviewed,
                isFavorite = wire.isFavorite,
                isInLaundry = wire.isInLaundry,
                searchText = wire.searchText,
                createdAt = wire.createdAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) garmentDao.insert(entity) else garmentDao.update(entity)
        return ApplyOutcome.Applied
    }

    private suspend fun mergeCollections(
        garmentId: Long,
        wire: GarmentWire,
        resolver: SyncIdResolver,
    ) {
        mergeSeasonsAndDressCodes(garmentId, wire)
        mergePalette(garmentId, wire, resolver)
        mergeMaterials(garmentId, wire, resolver)
        mergeTags(garmentId, wire, resolver)
    }

    private suspend fun mergeSeasonsAndDressCodes(
        garmentId: Long,
        wire: GarmentWire,
    ) {
        val current = garmentDao.getWithRelations(garmentId) ?: return
        val unionSeasons = (current.seasons.map { it.season } + wire.seasons).distinct()
        val unionDressCodes = (current.dressCodes.map { it.dressCode } + wire.dressCodes).distinct()
        garmentDao.clearSeasons(garmentId)
        garmentDao.insertSeasons(unionSeasons.map { GarmentSeasonCrossRef(garmentId, it) })
        garmentDao.clearDressCodes(garmentId)
        garmentDao.insertDressCodes(unionDressCodes.map { GarmentDressCodeCrossRef(garmentId, it) })
    }

    private suspend fun mergePalette(
        garmentId: Long,
        wire: GarmentWire,
        resolver: SyncIdResolver,
    ) {
        val current = garmentDao.getWithRelations(garmentId) ?: return
        val byColorId = current.palette.associateBy { it.colorId }.toMutableMap()
        wire.palette.forEach { entry ->
            val colorId = resolver.resolveLocalId("colors", entry.colorSyncId) ?: return@forEach
            byColorId[colorId] = GarmentColorPaletteCrossRef(garmentId, colorId, entry.weightPercent)
        }
        garmentDao.clearPalette(garmentId)
        garmentDao.insertPalette(byColorId.values.toList())
    }

    private suspend fun mergeMaterials(
        garmentId: Long,
        wire: GarmentWire,
        resolver: SyncIdResolver,
    ) {
        val current = garmentDao.getWithRelations(garmentId) ?: return
        val byMaterialId = current.materials.associateBy { it.materialId }.toMutableMap()
        wire.materials.forEach { entry ->
            val materialId = resolver.resolveLocalId("materials", entry.materialSyncId) ?: return@forEach
            byMaterialId[materialId] = GarmentMaterialCrossRef(garmentId, materialId, entry.percentage)
        }
        garmentDao.clearMaterials(garmentId)
        garmentDao.insertMaterials(byMaterialId.values.toList())
    }

    private suspend fun mergeTags(
        garmentId: Long,
        wire: GarmentWire,
        resolver: SyncIdResolver,
    ) {
        val current = garmentDao.getWithRelations(garmentId) ?: return
        val tagIds = current.tags.map { it.tagId }.toMutableSet()
        wire.tagSyncIds.forEach { syncId -> resolver.resolveLocalId("tags", syncId)?.let { tagIds.add(it) } }
        garmentDao.clearTags(garmentId)
        garmentDao.insertTags(tagIds.map { GarmentTagCrossRef(garmentId, it) })
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = garmentDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: \"${existing.name ?: "Untitled garment"}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                garmentDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
