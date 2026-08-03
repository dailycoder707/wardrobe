package com.wardrobe.app.feature.closet.edit

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag

@Immutable
data class EditGarmentFormState(
    val name: String = "",
    val categoryId: CategoryId? = null,
    val brandId: BrandId? = null,
    val primaryColorId: ColorId? = null,
    val size: String = "",
    val priceText: String = "",
    val condition: Condition? = null,
    val careNotes: String = "",
    val purchaseDate: String = "",
    val notes: String = "",
    val seasons: Set<Season> = emptySet(),
    val dressCodes: Set<DressCode> = emptySet(),
    val tagIds: Set<TagId> = emptySet(),
)

@Immutable
data class EditGarmentUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val form: EditGarmentFormState = EditGarmentFormState(),
    val categories: List<Category> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val colors: List<Color> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val didSave: Boolean = false,
)
