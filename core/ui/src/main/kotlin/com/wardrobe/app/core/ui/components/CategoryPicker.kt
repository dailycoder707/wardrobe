package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel

/**
 * A two-level TOP → SUB category picker, built on [DropdownField] — no
 * bespoke tree-picker component, since the widest SUB list (Tops) is only
 * ~8 items, well within a flat dropdown's comfort zone. Selecting a TOP
 * bucket with no subcategories (e.g. "Belts") selects that bucket itself as
 * [onSelect]'s value; selecting a TOP bucket that does have subcategories
 * only sets the tentative value until a SUB is chosen, at which point the
 * SUB becomes the real selection — matching how `Garment.categoryId` always
 * ends up pointing at a leaf-usable row either way.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedCategoryId: CategoryId?,
    onSelect: (CategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = categories.associateBy { it.id }
    val selectedCategory = selectedCategoryId?.let(byId::get)
    val selectedTopId =
        when (selectedCategory?.level) {
            null -> null
            CategoryLevel.TOP -> selectedCategory.id
            CategoryLevel.SUB -> selectedCategory.parentId
        }
    val subcategoriesForSelectedTop =
        categories.filter { it.level == CategoryLevel.SUB && it.parentId == selectedTopId }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField(
            label = "Category",
            options = categories.filter { it.level == CategoryLevel.TOP }.map { it.id to it.name },
            selected = selectedTopId,
            onSelect = { topId -> topId?.let(onSelect) },
        )
        if (subcategoriesForSelectedTop.isNotEmpty()) {
            DropdownField(
                label = "Subcategory",
                options = subcategoriesForSelectedTop.map { it.id to it.name },
                selected = selectedCategory?.id?.takeIf { selectedCategory.level == CategoryLevel.SUB },
                onSelect = { subId -> subId?.let(onSelect) },
            )
        }
    }
}
