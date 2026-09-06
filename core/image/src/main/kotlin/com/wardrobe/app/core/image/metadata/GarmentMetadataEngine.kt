package com.wardrobe.app.core.image.metadata

import android.graphics.Bitmap
import com.wardrobe.app.core.model.ai.MetadataSuggestion

/**
 * Add-to-Wardrobe v2's metadata-generation capability. Takes the already-
 * extracted garment cutout (never the original photo with a person in it —
 * `PrivacyPreprocessor` and the pipeline order both guarantee this) and
 * returns zero or more [MetadataSuggestion]s; a field this implementation
 * has no real signal for is simply absent from the list, never guessed.
 * Lives in `core:image`, not `core:ai`, matching
 * [com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine]/
 * [com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine] —
 * `GarmentImagePipeline` calls all three on-device-default interfaces
 * directly, and only `core:data`'s cloud-aware Router ever depends on
 * `core:ai`.
 */
interface GarmentMetadataEngine {
    suspend fun generateMetadata(cutout: Bitmap): List<MetadataSuggestion>
}
