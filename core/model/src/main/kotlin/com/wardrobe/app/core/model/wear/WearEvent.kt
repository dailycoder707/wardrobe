package com.wardrobe.app.core.model.wear

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.common.WeatherCacheId
import java.time.Instant
import java.time.LocalDate

/** [WearEvent.status] — added Phase 5d for Outfit Scheduling: a [PLANNED] row is
 * a future-dated intention to wear a garment/outfit (Calendar's "assign to a
 * day"), a [WORN] row is the original retrospective wear log every earlier
 * phase assumed. Stats (`StatsRepository`) only ever count [WORN] rows, so
 * scheduling tomorrow's outfit today never inflates cost-per-wear/usage stats
 * before it's actually worn. */
enum class WearEventStatus { PLANNED, WORN }

/**
 * A garment or outfit worn — or, as of Phase 5d, planned to be worn — on a
 * date. Exactly one of [garmentId]/[outfitId] must be non-null — enforced by
 * the repository layer, not the schema (Constitution rule 4; see
 * phase-3-persistence.md). A single garment can be logged without being forced
 * into an "outfit," directly addressing a documented pain point in the source-app
 * teardown. [date] carries no past/future constraint itself — [status] is what
 * distinguishes a completed wear from a scheduled one, not the date's position
 * relative to "today."
 */
data class WearEvent(
    val id: WearEventId,
    val date: LocalDate,
    val garmentId: GarmentId?,
    val outfitId: OutfitId?,
    val weatherCacheId: WeatherCacheId?,
    val occasionId: OccasionId?,
    val note: String?,
    val status: WearEventStatus = WearEventStatus.WORN,
    val createdAt: Instant,
) {
    init {
        require((garmentId == null) != (outfitId == null)) {
            "exactly one of garmentId/outfitId must be set, got garmentId=$garmentId outfitId=$outfitId"
        }
    }
}
