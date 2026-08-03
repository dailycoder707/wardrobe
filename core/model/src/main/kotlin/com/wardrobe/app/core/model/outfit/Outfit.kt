package com.wardrobe.app.core.model.outfit

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import java.time.Instant

enum class OutfitSource { USER_CREATED, AI_SUGGESTED, RESTYLED }

/** One garment's position within an outfit — `outfit_garments`, ordered by [layerSlot].
 * [layerSlot] is the persisted integer form of [OutfitSlot.slotIndex]; see that
 * enum for the named-slot mapping the Outfit Builder (Phase 5d) presents. */
data class OutfitGarmentSlot(
    val garmentId: GarmentId,
    val layerSlot: Int,
)

/**
 * [isFavorite]/[isArchived]/[notes]/[mood]/[seasons]/[dressCodes]/[tagIds] were
 * added Phase 5d for the Outfit Builder/Saved Looks metadata the master prompt
 * asks for. [isArchived] is a distinct lifecycle flag from [isSaved] — [isSaved]
 * separates a deliberately-kept look from an ephemeral AI-suggested-but-not-saved
 * one (Phase 6), while [isArchived] lets a saved look be hidden from the main
 * Saved Looks grid without deleting it (mirrors `Garment.status`'s STORED idea,
 * without needing a full status enum for outfits yet). [mood] is a short free-text
 * field, not an enum — no design doc defines a fixed mood taxonomy, and inventing
 * one would be presumptuous over-design for a field the user mostly free-types.
 */
data class Outfit(
    val id: OutfitId,
    val name: String?,
    val garments: List<OutfitGarmentSlot>,
    val occasionId: OccasionId?,
    val source: OutfitSource,
    val isSaved: Boolean,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String? = null,
    val mood: String? = null,
    val seasons: Set<Season> = emptySet(),
    val dressCodes: Set<DressCode> = emptySet(),
    val tagIds: List<TagId> = emptyList(),
    val photoUri: String?,
    val createdAt: Instant,
)
