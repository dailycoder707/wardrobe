package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.WaterproofLevel

/** Display labels for the fixed enum facets (`Season`/`DressCode`/`Fit`/
 * `GarmentGender`/`WaterproofLevel` carry no label metadata of their own —
 * see M17 architecture inventory). Centralized here since both
 * [ClosetFilterSheet] and [ActiveFilterChipsRow] need the same label for the
 * same value. */
fun Season.displayLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

fun DressCode.displayLabel(): String =
    name
        .lowercase()
        .replace('_', ' ')
        .replaceFirstChar(Char::uppercase)

fun Fit.displayLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

fun GarmentGender.displayLabel(): String =
    when (this) {
        GarmentGender.WOMENS -> "Women's"
        GarmentGender.MENS -> "Men's"
        GarmentGender.UNISEX -> "Unisex"
        GarmentGender.KIDS -> "Kids"
    }

fun WaterproofLevel.displayLabel(): String =
    when (this) {
        WaterproofLevel.NONE -> "Not waterproof"
        WaterproofLevel.WATER_RESISTANT -> "Water resistant"
        WaterproofLevel.WATERPROOF -> "Waterproof"
    }
