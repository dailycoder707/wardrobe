package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * No "profile id" column — there is exactly one style profile per installed app
 * (single-user product, ADR-004), so this table itself *is* the set. See
 * phase-3-persistence.md.
 */
@Entity(
    tableName = "style_profile_preferred_brands",
    foreignKeys = [
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StyleProfilePreferredBrandCrossRef(
    @androidx.room.PrimaryKey val brandId: Long,
)

@Entity(
    tableName = "style_profile_avoided_categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StyleProfileAvoidedCategoryCrossRef(
    @androidx.room.PrimaryKey val categoryId: Long,
)
