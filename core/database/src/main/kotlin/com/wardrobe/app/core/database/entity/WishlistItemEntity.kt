package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wishlist_items",
    indices = [Index("categoryId"), Index("brandId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WishlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoUri: String?,
    val notes: String?,
    val estimatedPrice: Double?,
    val currencyCode: String?,
    val categoryId: Long?,
    val brandId: Long?,
    val priority: Int?,
    val isPurchased: Boolean,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
