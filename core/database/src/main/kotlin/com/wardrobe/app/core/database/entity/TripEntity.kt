package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.trip.LuggageSize

@Entity(tableName = "trips", indices = [Index("syncId", unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val luggageSize: LuggageSize?,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
