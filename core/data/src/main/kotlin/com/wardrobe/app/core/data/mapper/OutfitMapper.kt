package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.dao.OutfitWithRelations
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import java.time.Instant

internal fun OutfitWithRelations.toDomain(): Outfit {
    val o = outfit
    return Outfit(
        id = OutfitId(o.id),
        name = o.name,
        garments =
            garments
                .sortedBy { it.layerSlot }
                .map { OutfitGarmentSlot(GarmentId(it.garmentId), it.layerSlot) },
        occasionId = o.occasionId?.let(::OccasionId),
        source = o.source,
        isSaved = o.isSaved,
        isFavorite = o.isFavorite,
        isArchived = o.isArchived,
        notes = o.notes,
        mood = o.mood,
        seasons = seasons.map { it.season }.toSet(),
        dressCodes = dressCodes.map { it.dressCode }.toSet(),
        tagIds = tags.map { TagId(it.tagId) },
        photoUri = o.photoUri,
        createdAt = Instant.ofEpochMilli(o.createdAt),
    )
}

internal fun Outfit.toEntity(): OutfitEntity =
    OutfitEntity(
        id = id.value,
        name = name,
        occasionId = occasionId?.value,
        source = source,
        isSaved = isSaved,
        isFavorite = isFavorite,
        isArchived = isArchived,
        notes = notes,
        mood = mood,
        photoUri = photoUri,
        createdAt = createdAt.toEpochMilli(),
    )
