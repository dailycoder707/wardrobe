package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Phase 10 — a private, on-device (optionally locally-synced) body
 * profile. Single active profile per device for v1 — see `BodyProfileDao`/
 * `BodyProfileRepositoryImpl` for how "single" is enforced at the
 * repository layer, not the schema layer (nothing here prevents a second
 * row; the app just never creates one in v1). */
@Entity(tableName = "body_profiles", indices = [Index("syncId", unique = true)])
data class BodyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
