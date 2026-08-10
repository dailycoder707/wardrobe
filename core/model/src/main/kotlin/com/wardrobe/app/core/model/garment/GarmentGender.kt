package com.wardrobe.app.core.model.garment

/**
 * The garment's own intended-for-gender fit/styling — a fixed vocabulary,
 * same shape as [Fit]/[SleeveLength]. Distinct from any future user
 * profile field; this describes the item, not its owner.
 */
enum class GarmentGender { WOMENS, MENS, UNISEX, KIDS }
