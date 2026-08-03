package com.wardrobe.app.feature.outfits.recommendations

import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** A brand-new suggestion carries the `OutfitId(0)` sentinel (same convention
 * Outfit Builder/Saved Looks use, see `phase-6-personal-wardrobe-stylist.md`)
 * until a Quick Action actually commits it — saving twice for the same
 * suggestion just returns the id already assigned the first time. */
internal suspend fun persistSelectedOutfit(
    outfitRepository: OutfitRepository,
    selected: RecommendedOutfitUiModel,
): OutfitId =
    if (selected.outfit.id.value != 0L) {
        selected.outfit.id
    } else {
        outfitRepository.saveOutfit(selected.outfit.copy(isSaved = true))
    }

internal suspend fun logOutfitWear(
    wearEventRepository: WearEventRepository,
    clock: Clock,
    outfitId: OutfitId,
    date: LocalDate,
) {
    val status = if (date.isAfter(LocalDate.now(clock))) WearEventStatus.PLANNED else WearEventStatus.WORN
    wearEventRepository.logWear(
        WearEvent(
            id = WearEventId(0),
            date = date,
            garmentId = null,
            outfitId = outfitId,
            weatherCacheId = null,
            occasionId = null,
            note = null,
            status = status,
            createdAt = Instant.now(clock),
        ),
    )
}

internal fun replaceSlotInUiModel(
    selected: RecommendedOutfitUiModel,
    slot: OutfitSlot,
    replacementId: GarmentId,
): ScoredOutfit {
    val updatedGarments =
        selected.outfit.garments.filterNot { OutfitSlot.fromIndex(it.layerSlot) == slot } +
            OutfitGarmentSlot(replacementId, slot.slotIndex)
    return ScoredOutfit(selected.outfit.copy(garments = updatedGarments), selected.score, selected.explanation, true)
}
