package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.BrandEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.FabricEntity
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.database.entity.OccasionEntity
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion

/** Small reference-data mappers — every one of these is a straight 1:1 field copy, no
 * relation resolution needed (unlike Garment/Outfit — see GarmentMapper). */

internal fun CategoryEntity.toDomain() =
    Category(
        id = CategoryId(id),
        name = name,
        parentId = parentId?.let(::CategoryId),
        level = level,
    )

internal fun ColorEntity.toDomain() = Color(id = ColorId(id), name = name, hexValue = hexValue)

internal fun MaterialEntity.toDomain() = Material(id = MaterialId(id), name = name)

internal fun FabricEntity.toDomain() = Fabric(id = FabricId(id), name = name)

internal fun BrandEntity.toDomain() = Brand(id = BrandId(id), name = name, logoUri = logoUri)

internal fun TagEntity.toDomain() = Tag(id = TagId(id), name = name)

internal fun OccasionEntity.toDomain() = Occasion(id = OccasionId(id), name = name)
