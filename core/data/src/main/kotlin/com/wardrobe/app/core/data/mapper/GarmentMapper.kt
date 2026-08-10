package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.dao.GarmentWithRelations
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.ImageMetadataId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.ColorPaletteEntry
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.FabricComposition
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageMetadata
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.MaterialComposition
import java.time.Instant
import java.time.LocalDate
import com.wardrobe.app.core.model.garment.Color as DomainColor

internal fun ImageMetadataEntity.toDomain() =
    ImageMetadata(
        id = ImageMetadataId(id),
        garmentId = GarmentId(garmentId),
        type = type,
        filePath = filePath,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        format = format,
        checksum = checksum,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

/**
 * The only mapper in this module that needs lookup maps rather than being a plain
 * 1:1 copy — see phase-5a-data-layer.md's mapping strategy. [colorsById]/
 * [materialsById] must contain every id referenced by [this]'s palette/materials or
 * that entry is silently dropped (rather than throwing) since a dangling reference
 * here means the reference row was deleted out from under a RESTRICT-protected FK,
 * which the schema should already prevent — see phase-3-persistence.md.
 */
internal fun GarmentWithRelations.toDomain(
    colorsById: Map<Long, DomainColor>,
    materialsById: Map<Long, Material>,
    fabricsById: Map<Long, Fabric>,
): Garment {
    val g: GarmentEntity = garment
    return Garment(
        id = GarmentId(g.id),
        name = g.name,
        categoryId = CategoryId(g.categoryId),
        primaryColorId = g.primaryColorId?.let(::ColorId),
        palette =
            palette.mapNotNull { ref ->
                colorsById[ref.colorId]?.let { color -> ColorPaletteEntry(color, ref.weightPercent) }
            },
        materials =
            materials.mapNotNull { ref ->
                materialsById[ref.materialId]?.let { material -> MaterialComposition(material, ref.percentage) }
            },
        tagIds = tags.map { TagId(it.tagId) },
        seasons = seasons.map { it.season }.toSet(),
        dressCodes = dressCodes.map { it.dressCode }.toSet(),
        pattern = g.pattern,
        fit = g.fit,
        length = g.length,
        sleeveLength = g.sleeveLength,
        warmthRating = g.warmthRating,
        breathabilityRating = g.breathabilityRating,
        brandId = g.brandId?.let(::BrandId),
        size = g.size,
        price = g.price?.let { amount -> g.currencyCode?.let { currency -> Money(amount, currency) } },
        purchaseDate = g.purchaseDate?.let(LocalDate::parse),
        condition = g.condition,
        careNotes = g.careNotes,
        notes = g.notes,
        status = g.status,
        isReviewed = g.isReviewed,
        isFavorite = g.isFavorite,
        isInLaundry = g.isInLaundry,
        images = images.map(ImageMetadataEntity::toDomain),
        createdAt = Instant.ofEpochMilli(g.createdAt),
        updatedAt = Instant.ofEpochMilli(g.updatedAt),
        secondaryColorId = g.secondaryColorId?.let(::ColorId),
        fabrics =
            fabrics.mapNotNull { ref ->
                fabricsById[ref.fabricId]?.let { fabric -> FabricComposition(fabric, ref.percentage) }
            },
        occasionIds = occasions.map { OccasionId(it.occasionId) },
        neckline = g.neckline,
        gender = g.gender,
        waterproofLevel = g.waterproofLevel,
    )
}

/** The reverse direction — used by `saveGarment`/`updateGarment`. Cross-ref rows are
 * built separately (`GarmentRepositoryImpl` inserts them in the same transaction),
 * since they depend on the entity's freshly-assigned id on insert. */
internal fun Garment.toEntity(searchText: String) =
    GarmentEntity(
        id = id.value,
        name = name,
        categoryId = categoryId.value,
        primaryColorId = primaryColorId?.value,
        pattern = pattern,
        fit = fit,
        length = length,
        sleeveLength = sleeveLength,
        warmthRating = warmthRating,
        breathabilityRating = breathabilityRating,
        brandId = brandId?.value,
        size = size,
        price = price?.amount,
        currencyCode = price?.currencyCode,
        purchaseDate = purchaseDate?.toString(),
        condition = condition,
        careNotes = careNotes,
        notes = notes,
        status = status,
        isReviewed = isReviewed,
        isFavorite = isFavorite,
        isInLaundry = isInLaundry,
        searchText = searchText,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        secondaryColorId = secondaryColorId?.value,
        neckline = neckline,
        gender = gender,
        waterproofLevel = waterproofLevel,
    )
