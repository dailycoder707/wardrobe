package com.wardrobe.app.feature.capture.review

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.AiProcessingSummary
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.ComparisonStage
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.QualityCheck
import com.wardrobe.app.core.model.garment.ReconstructionOutcome
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.model.outfit.Occasion

/** Only [categoryId] is required — every other field mirrors [Garment]'s own
 * nullable/default fields, since Save as Draft only needs a category.
 * [patternText]/[fit]/[length]/[sleeveLength] are M10 additions — the AI
 * metadata engine can suggest all four, but v1's form never collected them.
 * [secondaryColorId]/[fabricId]/[occasionIds]/[neckline]/[gender]/
 * [waterproofLevel] are AI Wardrobe Assistant Parts 1-3 additions — see
 * `Garment`'s own KDoc for why each is distinct from an existing field. */
@Immutable
data class GarmentMetadataFormState(
    val name: String = "",
    val categoryId: CategoryId? = null,
    val brandId: BrandId? = null,
    val primaryColorId: ColorId? = null,
    val materialId: MaterialId? = null,
    val patternText: String = "",
    val fit: Fit? = null,
    val length: GarmentLength? = null,
    val sleeveLength: SleeveLength? = null,
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
    val secondaryColorId: ColorId? = null,
    val fabricId: FabricId? = null,
    val occasionIds: Set<OccasionId> = emptySet(),
    val neckline: Neckline? = null,
    val gender: GarmentGender? = null,
    val waterproofLevel: WaterproofLevel? = null,
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
    val fabrics: List<Fabric> = emptyList(),
    val occasions: List<Occasion> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val didSave: Boolean = false,
    // --- M10: premium AI review additions ---
    val variants: List<ImageVariant> = emptyList(),
    val comparisonStages: List<ComparisonStage> = emptyList(),
    val whiteBackgroundImagePath: String? = null,
    val metadataSuggestions: List<MetadataSuggestion> = emptyList(),
    val aiProcessingSummary: AiProcessingSummary? = null,
    val qualityWarnings: List<QualityCheck> = emptyList(),
    val occlusionSeverity: Float? = null,
    val reconstructionOutcome: ReconstructionOutcome? = null,
    val retryingStage: ImageRetryStage? = null,
    /** AI Wardrobe Assistant Part 2 — non-null while the confidence-gated
     * auto-save countdown is actively counting down (3, 2, 1); null means
     * no auto-save is in progress (either not eligible, already
     * saved/cancelled, or still loading). See `AutoSaveGate`. */
    val autoSaveCountdownSeconds: Int? = null,
)
