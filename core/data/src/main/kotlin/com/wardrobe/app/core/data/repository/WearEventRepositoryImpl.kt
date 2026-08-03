package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.WearEventDao
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class WearEventRepositoryImpl
    @Inject
    constructor(
        private val dao: WearEventDao,
    ) : WearEventRepository {
        override fun observeEvents(range: DateRange): Flow<List<WearEvent>> =
            dao.observeInRange(range.start.toString(), range.end.toString()).map { rows -> rows.map { it.toDomain() } }

        /** [WearEvent]'s own constructor already enforces the garmentId-XOR-outfitId
         * invariant (core:model) — nothing to validate again here. */
        override suspend fun logWear(event: WearEvent): WearEventId =
            WearEventId(
                dao.insert(
                    event.toEntity().copy(
                        id = 0,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )

        /** Phase 8: syncId must survive an edit unchanged, same reasoning as
         * `GarmentRepositoryImpl.updateGarment`. */
        override suspend fun updateWear(event: WearEvent) {
            val existingSyncId = dao.getById(event.id.value)?.syncId.orEmpty()
            dao.update(event.toEntity().copy(syncId = existingSyncId, updatedAt = System.currentTimeMillis()))
        }

        override suspend fun deleteEvent(id: WearEventId) = dao.deleteById(id.value)

        override suspend fun clearDay(date: LocalDate) = dao.deleteForDate(date.toString())

        /** Copies every wear event on [from] onto [to] as a fresh, independent
         * [com.wardrobe.app.core.model.wear.WearEventStatus.PLANNED] row each —
         * Calendar's "Duplicate day" (Phase 5d). Deliberately a copy, not a link:
         * editing/clearing the duplicate must never affect the original day. */
        override suspend fun duplicateDay(
            from: LocalDate,
            to: LocalDate,
        ) {
            val sourceEvents = dao.getForDate(from.toString())
            val now = System.currentTimeMillis()
            sourceEvents.forEach { entity ->
                // Phase 8: a duplicate is a brand-new row, not the same logical
                // event — it needs its own syncId, never the source event's
                // (which would collide on the unique index the moment both
                // rows exist at once).
                dao.insert(
                    entity.copy(
                        id = 0,
                        date = to.toString(),
                        status = WearEventStatus.PLANNED,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = now,
                    ),
                )
            }
        }
    }
