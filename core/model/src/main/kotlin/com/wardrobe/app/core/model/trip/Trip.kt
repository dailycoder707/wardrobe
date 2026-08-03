package com.wardrobe.app.core.model.trip

import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.TripId
import java.time.Instant

enum class LuggageSize { CARRY_ON, CHECKED_SMALL, CHECKED_LARGE }

data class Trip(
    val id: TripId,
    val name: String?,
    val destination: String,
    val dateRange: DateRange,
    val activities: List<String>,
    val luggageSize: LuggageSize?,
    val createdAt: Instant,
)
