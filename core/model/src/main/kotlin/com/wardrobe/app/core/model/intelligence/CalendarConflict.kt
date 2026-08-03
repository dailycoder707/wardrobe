package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import java.time.LocalDate

/** Every conflict `WardrobeIntelligenceRepository.observeCalendarConflicts`
 * currently detects — a subtle badge on the affected calendar day, never a
 * popup/dialog (Constitution: calm, not intrusive). */
enum class ConflictReason { DUPLICATE_PLANNED_OUTFIT, GARMENT_IN_LAUNDRY, GARMENT_PACKED_ELSEWHERE }

data class CalendarConflict(
    val date: LocalDate,
    val reason: ConflictReason,
    val outfitId: OutfitId?,
    val garmentId: GarmentId?,
    val message: String,
)
