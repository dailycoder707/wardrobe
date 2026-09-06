package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.SleeveLength
import com.wardrobe.app.core.model.garment.WaterproofLevel

/**
 * See phase-3-persistence.md for the full field-by-field rationale, the index list,
 * and why `searchText` is deliberately not indexed (an infix `LIKE` scan, not a
 * prefix match). [isFavorite] was added in Phase 5c — a real gap in Phase 3's
 * schema discovered once the Closet/Garment Detail screens needed a favorite
 * toggle. No version bump: schema version 1 has never shipped to a real
 * install, so amending the entity directly (rather than a `Migration`) is safe
 * here — see `TECHNICAL_DEBT.md` if this project ever needs the rule for why
 * that stops being true once a real release exists.
 *
 * [secondaryColorId]/[neckline]/[gender]/[waterproofLevel] were added in
 * `MIGRATION_8_9` (a real migration this time — schema version 8 shipped
 * to a real install, so amending in place is no longer safe).
 */
@Entity(
    tableName = "garments",
    indices = [
        Index("categoryId"),
        Index("status"),
        Index("brandId"),
        Index("syncId", unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ColorEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryColorId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ColorEntity::class,
            parentColumns = ["id"],
            childColumns = ["secondaryColorId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class GarmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val categoryId: Long,
    val primaryColorId: Long?,
    val pattern: String?,
    val fit: Fit?,
    val length: GarmentLength?,
    val sleeveLength: SleeveLength?,
    val warmthRating: Int?,
    val breathabilityRating: Int?,
    val brandId: Long?,
    val size: String?,
    val price: Double?,
    val currencyCode: String?,
    val purchaseDate: String?,
    val condition: Condition?,
    val careNotes: String?,
    val notes: String? = null,
    val status: GarmentStatus,
    val isReviewed: Boolean,
    val isFavorite: Boolean = false,
    val isInLaundry: Boolean = false,
    val searchText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String = "",
    val secondaryColorId: Long? = null,
    val neckline: Neckline? = null,
    val gender: GarmentGender? = null,
    val waterproofLevel: WaterproofLevel? = null,
)
