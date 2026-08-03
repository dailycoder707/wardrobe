package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.wear.WearEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WearEventRepository {
    fun observeEvents(range: DateRange): Flow<List<WearEvent>>

    /** [WearEvent]'s own constructor already enforces the garmentId-XOR-outfitId
     * invariant (core:model) — this just persists an already-valid instance. */
    suspend fun logWear(event: WearEvent): WearEventId

    /** Backs Calendar's "quick reschedule" and "confirm as worn" (Phase 5d) —
     * an in-place edit of an existing row (see [WearEvent.status]). */
    suspend fun updateWear(event: WearEvent)

    suspend fun deleteEvent(id: WearEventId)

    /** Calendar's "Clear day" (Phase 5d) — removes every wear event on [date],
     * planned or worn. */
    suspend fun clearDay(date: LocalDate)

    /** Calendar's "Duplicate day" (Phase 5d) — copies every wear event on
     * [from] onto [to] as new [com.wardrobe.app.core.model.wear.WearEventStatus.PLANNED]
     * rows; does not touch or link back to [from]. */
    suspend fun duplicateDay(
        from: LocalDate,
        to: LocalDate,
    )
}
