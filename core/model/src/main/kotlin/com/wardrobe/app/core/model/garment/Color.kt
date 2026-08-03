package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.ColorId

data class Color(
    val id: ColorId,
    val name: String,
    val hexValue: String,
)

/** One entry in a garment's colour palette — see `garment_color_palette`, Phase 3. */
data class ColorPaletteEntry(
    val color: Color,
    val weightPercent: Int,
)
