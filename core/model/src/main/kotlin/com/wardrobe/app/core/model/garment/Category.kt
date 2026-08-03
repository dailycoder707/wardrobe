package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.CategoryId

enum class CategoryLevel { TOP, SUB }

data class Category(
    val id: CategoryId,
    val name: String,
    val parentId: CategoryId?,
    val level: CategoryLevel,
)
