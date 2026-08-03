package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentPlacementTemplateDao
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import com.wardrobe.app.core.domain.repository.TryOnPlacementRepository
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.GarmentPlacementTemplateId
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate
import com.wardrobe.app.core.model.tryon.PlacementTemplateType
import com.wardrobe.app.core.model.tryon.TryOnAnchorRegion
import com.wardrobe.app.core.tryon.placement.DefaultPlacementCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Resolves which [TryOnAnchorRegion] a garment anchors to from its own
 * category (the same `categoryId → CategoryEntity.name → OutfitSlot.classify`
 * lookup `OutfitPreviewViewModel` already does), so [defaultTemplateFor]/
 * [resetToAutoPlacement] never need an anchor region passed in from the
 * caller.
 */
class TryOnPlacementRepositoryImpl
    @Inject
    constructor(
        private val templateDao: GarmentPlacementTemplateDao,
        private val bodyProfileDao: BodyProfileDao,
        private val garmentDao: GarmentDao,
        private val categoryDao: CategoryDao,
    ) : TryOnPlacementRepository {
        override fun observeGarmentPlacementTemplates(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
        ): Flow<List<GarmentPlacementTemplate>> =
            templateDao
                .observeForGarment(bodyProfileId.value, garmentId.value)
                .map { rows -> rows.map { it.toDomain() } }

        override suspend fun saveGarmentPlacementTemplate(
            template: GarmentPlacementTemplate,
        ): GarmentPlacementTemplateId {
            val now = System.currentTimeMillis()
            val existing = if (template.id.value == 0L) null else templateDao.getById(template.id.value)
            val entity =
                GarmentPlacementTemplateEntity(
                    id = existing?.id ?: 0,
                    bodyProfileId = template.bodyProfileId.value,
                    garmentId = template.garmentId.value,
                    templateType = template.templateType.name,
                    customName = template.customName,
                    offsetXFraction = template.offsetXFraction,
                    offsetYFraction = template.offsetYFraction,
                    scale = template.scale,
                    rotationDegrees = template.rotationDegrees,
                    isUserAdjusted = template.isUserAdjusted,
                    placementSource = template.placementSource.name,
                    lastUsedAt = template.lastUsedAt?.toEpochMilli(),
                    updatedAt = now,
                    syncId = existing?.syncId ?: UUID.randomUUID().toString(),
                )
            val id =
                if (existing == null) {
                    templateDao.insert(entity)
                } else {
                    templateDao.update(entity)
                    entity.id
                }
            return GarmentPlacementTemplateId(id)
        }

        override suspend fun deleteGarmentPlacementTemplate(id: GarmentPlacementTemplateId) {
            templateDao.deleteById(id.value)
        }

        override suspend fun markTemplateUsed(id: GarmentPlacementTemplateId) {
            templateDao.markUsed(id.value, System.currentTimeMillis())
        }

        override suspend fun defaultTemplateFor(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
        ): GarmentPlacementTemplate {
            val candidates = observeGarmentPlacementTemplates(bodyProfileId, garmentId).first()
            val mostRecentlyUsed = candidates.filter { it.lastUsedAt != null }.maxByOrNull { it.lastUsedAt!! }
            val defaultTyped = candidates.firstOrNull { it.templateType == PlacementTemplateType.DEFAULT }
            return mostRecentlyUsed ?: defaultTyped ?: seedDefaultTemplate(bodyProfileId, garmentId)
        }

        override suspend fun resetToAutoPlacement(templateId: GarmentPlacementTemplateId): GarmentPlacementTemplate {
            val existing =
                requireNotNull(templateDao.getById(templateId.value)) { "Unknown placement template: $templateId" }
            val region = anchorRegionFor(GarmentId(existing.garmentId))
            val measurements = bodyProfileDao.getMeasurements(existing.bodyProfileId)?.toDomain()
            val calculated = DefaultPlacementCalculator.calculate(region, measurements)
            val updated =
                existing.copy(
                    offsetXFraction = calculated.offsetXFraction,
                    offsetYFraction = calculated.offsetYFraction,
                    scale = calculated.scale,
                    rotationDegrees = calculated.rotationDegrees,
                    isUserAdjusted = false,
                    placementSource = calculated.placementSource.name,
                    updatedAt = System.currentTimeMillis(),
                )
            templateDao.update(updated)
            return updated.toDomain()
        }

        private suspend fun seedDefaultTemplate(
            bodyProfileId: BodyProfileId,
            garmentId: GarmentId,
        ): GarmentPlacementTemplate {
            val region = anchorRegionFor(garmentId)
            val measurements = bodyProfileDao.getMeasurements(bodyProfileId.value)?.toDomain()
            val calculated = DefaultPlacementCalculator.calculate(region, measurements)
            val template =
                GarmentPlacementTemplate(
                    id = GarmentPlacementTemplateId(0),
                    bodyProfileId = bodyProfileId,
                    garmentId = garmentId,
                    templateType = PlacementTemplateType.DEFAULT,
                    customName = null,
                    offsetXFraction = calculated.offsetXFraction,
                    offsetYFraction = calculated.offsetYFraction,
                    scale = calculated.scale,
                    rotationDegrees = calculated.rotationDegrees,
                    isUserAdjusted = false,
                    placementSource = calculated.placementSource,
                    lastUsedAt = null,
                )
            val id = saveGarmentPlacementTemplate(template)
            return template.copy(id = id)
        }

        private suspend fun anchorRegionFor(garmentId: GarmentId): TryOnAnchorRegion {
            val categoryName = garmentCategoryName(garmentId)
            val slot = categoryName?.let(OutfitSlot::classify)
            return if (categoryName == null || slot == null) {
                TryOnAnchorRegion.FULL_TORSO
            } else {
                val accessory = if (slot == OutfitSlot.ACCESSORIES) AccessoryCategory.classify(categoryName) else null
                val jewelry = if (slot == OutfitSlot.JEWELRY) JewelryCategory.classify(categoryName) else null
                TryOnAnchorRegion.classify(slot, accessory, jewelry)
            }
        }

        private suspend fun garmentCategoryName(garmentId: GarmentId): String? {
            val garment = garmentDao.getById(garmentId.value) ?: return null
            return categoryDao.getById(garment.categoryId)?.name
        }
    }
