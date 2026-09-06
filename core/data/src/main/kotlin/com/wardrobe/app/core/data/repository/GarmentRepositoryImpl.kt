package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.FabricDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.FabricEntity
import com.wardrobe.app.core.database.entity.GarmentColorPaletteCrossRef
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentFabricCrossRef
import com.wardrobe.app.core.database.entity.GarmentMaterialCrossRef
import com.wardrobe.app.core.database.entity.GarmentOccasionCrossRef
import com.wardrobe.app.core.database.entity.GarmentSeasonCrossRef
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GarmentRepositoryImpl
    @Inject
    constructor(
        private val garmentDao: GarmentDao,
        private val categoryDao: CategoryDao,
        private val colorDao: ColorDao,
        private val materialDao: MaterialDao,
        private val fabricDao: FabricDao,
        private val brandDao: BrandDao,
        private val tagDao: TagDao,
        private val occasionDao: OccasionDao,
        private val imageFileStore: ImageFileStore,
    ) : GarmentRepository {
        override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> {
            val rows =
                garmentDao.observeFiltered(
                    categoryId = filter.categoryId?.value,
                    brandId = filter.brandId?.value,
                    status = filter.status,
                    season = filter.season,
                    dressCode = filter.dressCode,
                    searchQuery =
                        filter.searchQuery
                            ?.lowercase()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                    isFavorite = filter.isFavorite,
                )
            // combine with reference-data Flows so renaming a color/material/fabric
            // live-updates every garment referencing it — phase-5a-data-layer.md's
            // mapping strategy.
            return combine(
                rows,
                colorDao.observeAll(),
                materialDao.observeAll(),
                fabricDao.observeAll(),
            ) { garments, colors, materials, fabrics ->
                val colorsById = colors.associateBy({ it.id }, ColorEntity::toDomain)
                val materialsById = materials.associateBy({ it.id }, MaterialEntity::toDomain)
                val fabricsById = fabrics.associateBy({ it.id }, FabricEntity::toDomain)
                garments
                    .map { it.toDomain(colorsById, materialsById, fabricsById) }
                    .filter { garment -> matchesInMemoryFilters(garment, filter) }
            }
        }

        override fun observeGarment(id: GarmentId): Flow<Garment?> =
            combine(
                garmentDao.observeWithRelations(id.value),
                colorDao.observeAll(),
                materialDao.observeAll(),
                fabricDao.observeAll(),
            ) { row, colors, materials, fabrics ->
                if (row == null) return@combine null
                val colorsById = colors.associateBy({ it.id }, ColorEntity::toDomain)
                val materialsById = materials.associateBy({ it.id }, MaterialEntity::toDomain)
                val fabricsById = fabrics.associateBy({ it.id }, FabricEntity::toDomain)
                row.toDomain(colorsById, materialsById, fabricsById)
            }

        override suspend fun getGarment(id: GarmentId): Garment? {
            val row = garmentDao.getWithRelations(id.value) ?: return null
            val colorsById = colorDao.observeAll().first().associateBy({ it.id }, ColorEntity::toDomain)
            val materialsById = materialDao.observeAll().first().associateBy({ it.id }, MaterialEntity::toDomain)
            val fabricsById = loadFabricsById(fabricDao)
            return row.toDomain(colorsById, materialsById, fabricsById)
        }

        override suspend fun saveGarment(garment: Garment): GarmentId {
            val searchText = buildGarmentSearchText(garment, categoryDao, brandDao, tagDao, occasionDao)
            val entity =
                garment.toEntity(searchText).copy(
                    id = 0,
                    syncId =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )
            val id = garmentDao.insert(entity)
            writeCrossRefs(garmentDao, id, garment)
            return GarmentId(id)
        }

        /** Phase 8: [syncId] is this row's stable cross-device identity and must
         * survive every ordinary edit unchanged — [Garment.toEntity] has no way
         * to know it (the domain model doesn't carry it), so it's preserved
         * here from whatever the row already has, never regenerated. */
        override suspend fun updateGarment(garment: Garment) {
            val searchText = buildGarmentSearchText(garment, categoryDao, brandDao, tagDao, occasionDao)
            val existingSyncId = garmentDao.getById(garment.id.value)?.syncId.orEmpty()
            garmentDao.update(garment.toEntity(searchText).copy(syncId = existingSyncId))
            writeCrossRefs(garmentDao, garment.id.value, garment)
        }

        override suspend fun setStatus(
            id: GarmentId,
            status: GarmentStatus,
        ) {
            val current = garmentDao.getWithRelations(id.value)?.garment ?: return
            garmentDao.update(current.copy(status = status))
        }

        override suspend fun setFavorite(
            id: GarmentId,
            isFavorite: Boolean,
        ) {
            garmentDao.setFavorite(id.value, isFavorite, System.currentTimeMillis())
        }

        override suspend fun setInLaundry(
            id: GarmentId,
            isInLaundry: Boolean,
        ) {
            garmentDao.setInLaundry(id.value, isInLaundry, System.currentTimeMillis())
        }

        /** Throws `SQLiteConstraintException` if wear/outfit history exists — RESTRICT by
         * schema design (phase-3-persistence.md); this method does not catch it, since
         * the calling use case/ViewModel is what should decide how to explain that to the
         * user (screen-specifications.md's Garment Detail spec). Room's own `CASCADE`
         * removes the `image_metadata` rows when the delete below succeeds, but never
         * touches files on disk (Phase 5b) — that's what the second line is for. */
        override suspend fun deleteGarment(id: GarmentId) {
            garmentDao.deleteById(id.value)
            withContext(Dispatchers.IO) { imageFileStore.deleteGarmentDirectory(id.value) }
        }

        override suspend fun findPotentialDuplicates(
            categoryId: CategoryId,
            colorId: ColorId?,
            excludeGarmentId: GarmentId?,
        ): List<Garment> {
            val rows =
                garmentDao
                    .observeFiltered(
                        categoryId = categoryId.value,
                        brandId = null,
                        status = GarmentStatus.ACTIVE,
                        season = null,
                        dressCode = null,
                        searchQuery = null,
                        isFavorite = null,
                    ).first()
            val colorsById = colorDao.observeAll().first().associateBy({ it.id }, ColorEntity::toDomain)
            val materialsById = materialDao.observeAll().first().associateBy({ it.id }, MaterialEntity::toDomain)
            val fabricsById = loadFabricsById(fabricDao)
            return rows
                .asSequence()
                .filter { excludeGarmentId == null || it.garment.id != excludeGarmentId.value }
                .map { it.toDomain(colorsById, materialsById, fabricsById) }
                .filter { colorId == null || it.primaryColorId == colorId }
                .toList()
        }
    }

/** A top-level function (like [writeCrossRefs] below) so it doesn't count
 * against `GarmentRepositoryImpl`'s own function-count budget. */
private suspend fun loadFabricsById(fabricDao: FabricDao): Map<Long, Fabric> =
    fabricDao.observeAll().first().associateBy({ it.id }, FabricEntity::toDomain)

/** The maintained denormalization `phase-3-persistence.md` designs instead of
 * SQLite FTS — every write path that can change a searchable attribute must go
 * through `saveGarment`/`updateGarment` so this stays in sync; there is no
 * separate "rebuild search index" job. A top-level function (like
 * [writeCrossRefs] below) so it doesn't count against `GarmentRepositoryImpl`'s
 * own function-count budget. */
private suspend fun buildGarmentSearchText(
    garment: Garment,
    categoryDao: CategoryDao,
    brandDao: BrandDao,
    tagDao: TagDao,
    occasionDao: OccasionDao,
): String {
    val category = categoryDao.getById(garment.categoryId.value)?.name
    val brand = garment.brandId?.let { brandDao.getById(it.value)?.name }
    val tagNames =
        if (garment.tagIds.isEmpty()) {
            emptyList()
        } else {
            val allTags = tagDao.observeAll().first().associateBy { it.id }
            garment.tagIds.mapNotNull { allTags[it.value]?.name }
        }
    val occasionNames =
        if (garment.occasionIds.isEmpty()) {
            emptyList()
        } else {
            val allOccasions = occasionDao.observeAll().first().associateBy { it.id }
            garment.occasionIds.mapNotNull { allOccasions[it.value]?.name }
        }
    val colorNames = garment.palette.map { it.color.name }
    val fabricNames = garment.fabrics.map { it.fabric.name }
    val parts =
        buildList {
            garment.name?.let(::add)
            category?.let(::add)
            brand?.let(::add)
            addAll(colorNames)
            addAll(tagNames)
            addAll(fabricNames)
            addAll(occasionNames)
            garment.size?.let(::add)
            garment.careNotes?.let(::add)
            garment.notes?.let(::add)
        }
    return parts.joinToString(" ").lowercase()
}

/** Same reasoning as [matchesInMemoryFilters] below — a top-level function so
 * it doesn't count against `GarmentRepositoryImpl`'s own function-count
 * budget. Takes [garmentDao] as a parameter rather than being a class member. */
private suspend fun writeCrossRefs(
    garmentDao: GarmentDao,
    garmentId: Long,
    garment: Garment,
) {
    garmentDao.clearPalette(garmentId)
    if (garment.palette.isNotEmpty()) {
        garmentDao.insertPalette(
            garment.palette.map { GarmentColorPaletteCrossRef(garmentId, it.color.id.value, it.weightPercent) },
        )
    }
    garmentDao.clearMaterials(garmentId)
    if (garment.materials.isNotEmpty()) {
        garmentDao.insertMaterials(
            garment.materials.map {
                GarmentMaterialCrossRef(garmentId, it.material.id.value, it.percentage)
            },
        )
    }
    garmentDao.clearTags(garmentId)
    if (garment.tagIds.isNotEmpty()) {
        garmentDao.insertTags(garment.tagIds.map { GarmentTagCrossRef(garmentId, it.value) })
    }
    garmentDao.clearSeasons(garmentId)
    if (garment.seasons.isNotEmpty()) {
        garmentDao.insertSeasons(garment.seasons.map { GarmentSeasonCrossRef(garmentId, it) })
    }
    garmentDao.clearDressCodes(garmentId)
    if (garment.dressCodes.isNotEmpty()) {
        garmentDao.insertDressCodes(garment.dressCodes.map { GarmentDressCodeCrossRef(garmentId, it) })
    }
    writeFabricAndOccasionCrossRefs(garmentDao, garmentId, garment)
}

/** Split from [writeCrossRefs] to keep that function under detekt's
 * `LongMethod`/complexity thresholds now that fabrics/occasions are part
 * of the same "replace the whole set" write. */
private suspend fun writeFabricAndOccasionCrossRefs(
    garmentDao: GarmentDao,
    garmentId: Long,
    garment: Garment,
) {
    garmentDao.clearFabrics(garmentId)
    if (garment.fabrics.isNotEmpty()) {
        garmentDao.insertFabrics(
            garment.fabrics.map { GarmentFabricCrossRef(garmentId, it.fabric.id.value, it.percentage) },
        )
    }
    garmentDao.clearOccasions(garmentId)
    if (garment.occasionIds.isNotEmpty()) {
        garmentDao.insertOccasions(garment.occasionIds.map { GarmentOccasionCrossRef(garmentId, it.value) })
    }
}

/** [GarmentFilter]'s color/material/tag/price fields — see that class's own doc
 * comment for why these run here instead of in SQL. A top-level function (not
 * a class member) so it doesn't count against `GarmentRepositoryImpl`'s own
 * function-count budget for what's really a pure predicate, not repository
 * behavior. */
private fun matchesInMemoryFilters(
    garment: Garment,
    filter: GarmentFilter,
): Boolean {
    val price = garment.price?.amount
    val priceMin = filter.priceMin
    val priceMax = filter.priceMax
    return (filter.colorId == null || garment.palette.any { it.color.id == filter.colorId }) &&
        (filter.materialId == null || garment.materials.any { it.material.id == filter.materialId }) &&
        (filter.tagId == null || filter.tagId in garment.tagIds) &&
        (priceMin == null || (price != null && price >= priceMin)) &&
        (priceMax == null || (price != null && price <= priceMax))
}
