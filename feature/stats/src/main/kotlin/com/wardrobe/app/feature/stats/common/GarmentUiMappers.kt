package com.wardrobe.app.feature.stats.common

import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType

/** Same mapping every other feature module keeps its own copy of — modules
 * don't depend on each other (ADR-010). [metric] is the one-line reason this
 * garment appears in whichever insight list is rendering it. */
fun Garment.toInsightItem(metric: String): InsightItemUiModel =
    InsightItemUiModel(
        id = id.value,
        isOutfit = false,
        thumbnailPath = images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath,
        title = name?.takeUnless { it.isBlank() } ?: "Untitled item",
        metric = metric,
    )
