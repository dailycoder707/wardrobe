package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.MaterialId

data class Material(
    val id: MaterialId,
    val name: String,
)

/** One fabric component of a garment (garments are often blends) — `garment_materials`. */
data class MaterialComposition(
    val material: Material,
    val percentage: Int?,
)
