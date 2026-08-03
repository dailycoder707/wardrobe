package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.WishlistItemEntity
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.common.WishlistItemId
import com.wardrobe.app.core.model.wishlist.WishlistItem
import java.time.Instant

internal fun WishlistItemEntity.toDomain() =
    WishlistItem(
        id = WishlistItemId(id),
        name = name,
        photoUri = photoUri,
        notes = notes,
        estimatedPrice = estimatedPrice?.let { amount -> currencyCode?.let { Money(amount, it) } },
        categoryId = categoryId?.let(::CategoryId),
        brandId = brandId?.let(::BrandId),
        priority = priority,
        isPurchased = isPurchased,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

internal fun WishlistItem.toEntity() =
    WishlistItemEntity(
        id = id.value,
        name = name,
        photoUri = photoUri,
        notes = notes,
        estimatedPrice = estimatedPrice?.amount,
        currencyCode = estimatedPrice?.currencyCode,
        categoryId = categoryId?.value,
        brandId = brandId?.value,
        priority = priority,
        isPurchased = isPurchased,
        createdAt = createdAt.toEpochMilli(),
    )
