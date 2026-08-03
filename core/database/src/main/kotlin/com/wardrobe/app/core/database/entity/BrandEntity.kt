package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "brands", indices = [Index("name", unique = true), Index("syncId", unique = true)])
data class BrandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val logoUri: String?,
    val syncId: String = "",
    val updatedAt: Long = 0,
)
