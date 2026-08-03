package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "colors", indices = [Index("syncId", unique = true)])
data class ColorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hexValue: String,
    val syncId: String = "",
    val updatedAt: Long = 0,
)
