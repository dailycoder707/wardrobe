package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.FabricDao
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.entity.BrandEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.FabricEntity
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.database.entity.OccasionEntity
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.model.garment.CategoryLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val syncJson = Json { ignoreUnknownKeys = true }

/** The local copy wins ties (`>=`) — an identical `updatedAt` on both sides
 * (the common case for a row neither device has touched since pairing)
 * must not repeatedly "apply" a no-op write every single sync. */
internal fun isRemoteNewer(
    localUpdatedAt: Long,
    remoteUpdatedAt: Long,
): Boolean = remoteUpdatedAt > localUpdatedAt

/** A generic edit/delete conflict summary — every reference-table handler
 * below needs exactly this shape, so it lives here rather than being
 * repeated six times. */
internal fun renamedConflict(localName: String): ApplyOutcome.EditDeleteConflict =
    ApplyOutcome.EditDeleteConflict(
        localSummary = "Edited on this device: renamed to \"$localName\"",
        remoteSummary = "Deleted on the other device",
    )

@Serializable
private data class TagWire(
    val name: String,
)

class TagSyncHandler(
    private val dao: TagDao,
) : SyncEntityHandler {
    override val tableName = "tags"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao.getBySyncId(syncId)?.let { syncJson.encodeToString(TagWire.serializer(), TagWire(it.name)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(TagWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                TagEntity(id = existing?.id ?: 0, name = wire.name, syncId = syncId, updatedAt = remoteUpdatedAt)
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class OccasionWire(
    val name: String,
)

class OccasionSyncHandler(
    private val dao: OccasionDao,
) : SyncEntityHandler {
    override val tableName = "occasions"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao.getBySyncId(syncId)?.let { syncJson.encodeToString(OccasionWire.serializer(), OccasionWire(it.name)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(OccasionWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                OccasionEntity(id = existing?.id ?: 0, name = wire.name, syncId = syncId, updatedAt = remoteUpdatedAt)
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class MaterialWire(
    val name: String,
)

class MaterialSyncHandler(
    private val dao: MaterialDao,
) : SyncEntityHandler {
    override val tableName = "materials"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao.getBySyncId(syncId)?.let { syncJson.encodeToString(MaterialWire.serializer(), MaterialWire(it.name)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(MaterialWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                MaterialEntity(id = existing?.id ?: 0, name = wire.name, syncId = syncId, updatedAt = remoteUpdatedAt)
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class FabricWire(
    val name: String,
)

/** Mirrors [MaterialSyncHandler] exactly — see [com.wardrobe.app.core.model.garment.Fabric]'s
 * KDoc for why fabric is a separate reference table from material. */
class FabricSyncHandler(
    private val dao: FabricDao,
) : SyncEntityHandler {
    override val tableName = "fabrics"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao.getBySyncId(syncId)?.let { syncJson.encodeToString(FabricWire.serializer(), FabricWire(it.name)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(FabricWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                FabricEntity(id = existing?.id ?: 0, name = wire.name, syncId = syncId, updatedAt = remoteUpdatedAt)
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class BrandWire(
    val name: String,
    val logoUri: String?,
)

class BrandSyncHandler(
    private val dao: BrandDao,
) : SyncEntityHandler {
    override val tableName = "brands"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao.getBySyncId(syncId)?.let { syncJson.encodeToString(BrandWire.serializer(), BrandWire(it.name, it.logoUri)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(BrandWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                BrandEntity(
                    id = existing?.id ?: 0,
                    name = wire.name,
                    logoUri = wire.logoUri,
                    syncId = syncId,
                    updatedAt = remoteUpdatedAt,
                )
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class ColorWire(
    val name: String,
    val hexValue: String,
)

class ColorSyncHandler(
    private val dao: ColorDao,
) : SyncEntityHandler {
    override val tableName = "colors"

    override suspend fun currentFieldsJson(syncId: String): String? =
        dao
            .getBySyncId(
                syncId,
            )?.let { syncJson.encodeToString(ColorWire.serializer(), ColorWire(it.name, it.hexValue)) }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(ColorWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val entity =
                ColorEntity(
                    id = existing?.id ?: 0,
                    name = wire.name,
                    hexValue = wire.hexValue,
                    syncId = syncId,
                    updatedAt = remoteUpdatedAt,
                )
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class CategoryWire(
    val name: String,
    val parentSyncId: String?,
    val level: CategoryLevel,
)

/** The only reference table with a foreign key (self-referencing
 * `parentId`) — resolved via [resolver] like every other cross-table
 * reference in this sync engine, never copied as a raw local id. */
class CategorySyncHandler(
    private val dao: CategoryDao,
) : SyncEntityHandler {
    override val tableName = "categories"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = dao.getBySyncId(syncId)
        return entity?.let {
            val parentSyncId = it.parentId?.let { parentId -> dao.getById(parentId)?.syncId }
            syncJson.encodeToString(CategoryWire.serializer(), CategoryWire(it.name, parentSyncId, it.level))
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(CategoryWire.serializer(), fieldsJson)
        val existing = dao.getBySyncId(syncId)
        val remoteWins = existing == null || isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (remoteWins) {
            val parentId = wire.parentSyncId?.let { resolver.resolveLocalId(tableName, it) }
            val entity =
                CategoryEntity(
                    id = existing?.id ?: 0,
                    name = wire.name,
                    parentId = parentId,
                    level = wire.level,
                    syncId = syncId,
                    updatedAt = remoteUpdatedAt,
                )
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        return if (remoteWins) ApplyOutcome.Applied else ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = dao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                renamedConflict(existing.name)
            }

            else -> {
                dao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
