package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.common.OccasionId
import java.time.Instant
import java.time.LocalDate

enum class GarmentStatus { ACTIVE, STORED, DONATING, REPAIR, SOLD }

enum class Fit { SLIM, REGULAR, RELAXED, OVERSIZED }

enum class GarmentLength { CROP, REGULAR, LONG }

enum class SleeveLength { SLEEVELESS, SHORT, THREE_QUARTER, LONG }

enum class Condition { NEW, EXCELLENT, GOOD, FAIR, WORN }

/**
 * One physical item the user owns. `warmthRating`/`breathabilityRating` are a 1..5
 * scale used by the Phase 6 styling engine's hard weather filter
 * (phase-1-architecture.md, "Quality bar for the styling engine") — validated at the
 * use-case layer, not the schema (Constitution rule 4: don't imply the DB guarantees a
 * range check it doesn't).
 *
 * Every attribute the capture pipeline can only guess at (colour, category, season,
 * dress code) is surfaced in the UI as an editable suggestion with a confidence
 * signal while [isReviewed] is false — never presented as fact (Constitution rule 7).
 */
data class Garment(
    val id: GarmentId,
    val name: String?,
    val categoryId: CategoryId,
    val primaryColorId: ColorId?,
    val palette: List<ColorPaletteEntry>,
    val materials: List<MaterialComposition>,
    val tagIds: List<com.wardrobe.app.core.model.common.TagId>,
    val seasons: Set<Season>,
    val dressCodes: Set<DressCode>,
    val pattern: String?,
    val fit: Fit?,
    val length: GarmentLength?,
    val sleeveLength: SleeveLength?,
    val warmthRating: Int?,
    val breathabilityRating: Int?,
    val brandId: BrandId?,
    val size: String?,
    val price: Money?,
    val purchaseDate: LocalDate?,
    val condition: Condition?,
    val careNotes: String?,
    val status: GarmentStatus,
    val isReviewed: Boolean,
    val isFavorite: Boolean,
    val images: List<ImageMetadata>,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** A temporary, manually-toggled "don't recommend this right now" flag for the
     * Phase 6 styling engine — distinct from [status] (a permanent lifecycle state)
     * and from [DressCode]/[Season] fit. Defaulted so every existing construction
     * site across the codebase keeps compiling unchanged (the same additive,
     * nullable-default-column approach Phase 5d's `MIGRATION_1_2` used for
     * `outfits.isFavorite`/`isArchived`). */
    val isInLaundry: Boolean = false,
    /** General free-text notes the user enters at add/edit time — distinct from
     * [careNotes] (care-instruction text specifically). Added alongside the
     * Add-to-Wardrobe ingestion flow; defaulted so every existing construction
     * site keeps compiling unchanged. */
    val notes: String? = null,
    /** The item's secondary color, if one was confidently distinguished from
     * [primaryColorId] — a single slot, not the full multi-color [palette],
     * matching how [primaryColorId] is itself a single-slot convenience over
     * that same list. */
    val secondaryColorId: ColorId? = null,
    /** Fabric/weave composition (Denim, Jersey, Twill...) — deliberately
     * distinct from [materials] (fiber content: Cotton, Wool, Polyester...);
     * `garment_fabrics` mirrors `garment_materials`' shape exactly. */
    val fabrics: List<FabricComposition> = emptyList(),
    /** Occasions this item suits (`garment_occasions`, reusing the existing,
     * user-extensible [com.wardrobe.app.core.model.outfit.Occasion] reference
     * table already used by `Outfit`) — deliberately independent of
     * [dressCodes] (formality), not a redundant re-encoding of it. */
    val occasionIds: List<OccasionId> = emptyList(),
    val neckline: Neckline? = null,
    val gender: GarmentGender? = null,
    val waterproofLevel: WaterproofLevel? = null,
) {
    init {
        warmthRating?.let { require(it in RATING_RANGE) { "warmthRating must be $RATING_RANGE, was $it" } }
        breathabilityRating?.let {
            require(it in RATING_RANGE) { "breathabilityRating must be $RATING_RANGE, was $it" }
        }
    }

    companion object {
        /** The 1..5 scale the Phase 6 styling engine's weather filter scores against. */
        private val RATING_RANGE = 1..5
    }
}
