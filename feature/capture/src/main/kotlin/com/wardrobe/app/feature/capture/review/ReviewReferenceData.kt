package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion

/** The subset of reference data [autoFillForm] needs to resolve a
 * suggestion's plain-text value into a real reference-data ID. [tags] is
 * needed for `STYLE_TAG` suggestions (AI Wardrobe Assistant Parts 1-3) —
 * resolved into the same [GarmentMetadataFormState.tagIds] a user's own
 * tag picks use, never a new tag row (Constitution rule 4, same as every
 * other reference-backed field here). */
internal data class ReviewReferenceData(
    val categories: List<Category>,
    val brands: List<Brand>,
    val colors: List<Color>,
    val materials: List<Material>,
    val fabrics: List<Fabric>,
    val occasions: List<Occasion>,
    val tags: List<Tag>,
)
