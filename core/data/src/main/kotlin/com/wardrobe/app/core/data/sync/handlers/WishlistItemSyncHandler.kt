package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.WishlistDao
import com.wardrobe.app.core.database.entity.WishlistItemEntity
import kotlinx.serialization.Serializable

@Serializable
private data class WishlistItemWire(
    val name: String,
    val photoUri: String?,
    val notes: String?,
    val estimatedPrice: Double?,
    val currencyCode: String?,
    val categorySyncId: String?,
    val brandSyncId: String?,
    val priority: Int?,
    val isPurchased: Boolean,
    val createdAt: Long,
)

class WishlistItemSyncHandler(
    private val wishlistDao: WishlistDao,
    private val categoryDao: CategoryDao,
    private val brandDao: BrandDao,
) : SyncEntityHandler {
    override val tableName = "wishlist_items"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = wishlistDao.getBySyncId(syncId) ?: return null
        val wire =
            WishlistItemWire(
                name = entity.name,
                photoUri = entity.photoUri,
                notes = entity.notes,
                estimatedPrice = entity.estimatedPrice,
                currencyCode = entity.currencyCode,
                categorySyncId = entity.categoryId?.let { categoryDao.getById(it)?.syncId },
                brandSyncId = entity.brandId?.let { brandDao.getById(it)?.syncId },
                priority = entity.priority,
                isPurchased = entity.isPurchased,
                createdAt = entity.createdAt,
            )
        return syncJson.encodeToString(WishlistItemWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(WishlistItemWire.serializer(), fieldsJson)
        val existing = wishlistDao.getBySyncId(syncId)
        if (existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val categoryId = wire.categorySyncId?.let { resolver.resolveLocalId("categories", it) }
        val brandId = wire.brandSyncId?.let { resolver.resolveLocalId("brands", it) }
        val entity =
            WishlistItemEntity(
                id = existing?.id ?: 0,
                name = wire.name,
                photoUri = wire.photoUri,
                notes = wire.notes,
                estimatedPrice = wire.estimatedPrice,
                currencyCode = wire.currencyCode,
                categoryId = categoryId,
                brandId = brandId,
                priority = wire.priority,
                isPurchased = wire.isPurchased,
                createdAt = wire.createdAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) wishlistDao.insert(entity) else wishlistDao.update(entity)
        return ApplyOutcome.Applied
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = wishlistDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: \"${existing.name}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                wishlistDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
