package com.wardrobe.app.feature.capture.review

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag

/** Only [categoryId] is required — every other field mirrors [Garment]'s own
 * nullable/default fields, since Save as Draft only needs a category. */
@Immutable
data class GarmentMetadataFormState(
    val name: String = "",
    val categoryId: CategoryId? = null,
    val brandId: BrandId? = null,
    val primaryColorId: ColorId? = null,
    val materialId: MaterialId? = null,
    val size: String = "",
    val priceText: String = "",
    val purchaseDate: String = "",
    val isFavorite: Boolean = false,
    val isInLaundry: Boolean = false,
    val status: GarmentStatus = GarmentStatus.ACTIVE,
    val condition: Condition? = null,
    val careNotes: String = "",
    val notes: String = "",
    val seasons: Set<Season> = emptySet(),
    val dressCodes: Set<DressCode> = emptySet(),
    val tagIds: Set<TagId> = emptySet(),
)

@Immutable
data class GarmentReviewMetadataUiState(
    val isLoading: Boolean = true,
    /** True if the staged image was lost (e.g. a process death between
     * staging finishing and this screen opening) — the queue item has
     * already been reset to `PENDING` to restage; this screen has nothing
     * to show and should navigate back to the queue. */
    val needsRestage: Boolean = false,
    val previewImagePath: String? = null,
    val usedOriginalFallback: Boolean = false,
    val checksumDuplicateGarmentName: String? = null,
    val potentialDuplicates: List<Garment> = emptyList(),
    val form: GarmentMetadataFormState = GarmentMetadataFormState(),
    val categories: List<Category> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val colors: List<Color> = emptyList(),
    val materials: List<Material> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val didSave: Boolean = false,
)
