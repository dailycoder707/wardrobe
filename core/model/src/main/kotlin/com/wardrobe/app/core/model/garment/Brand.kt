package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.BrandId

data class Brand(
    val id: BrandId,
    val name: String,
    val logoUri: String?,
)
