package com.wardrobe.app.core.data.sync

/** Result of applying one incoming remote change (Phase 8). */
sealed interface ApplyOutcome {
    /** The remote row was newer (or the row didn't exist locally yet) and
     * has now been written. */
    data object Applied : ApplyOutcome

    /** The local row was newer — the remote change is correctly discarded,
     * per "newest modification wins." Not a conflict: this is the ordinary,
     * expected outcome whenever the two devices' clocks/edits interleave
     * normally. */
    data object LocalNewerIgnored : ApplyOutcome

    /** The one case deterministic rules can't settle safely: this row was
     * edited on one device and deleted on the other since they last synced.
     * Surfaced as a [com.wardrobe.app.core.model.sync.SyncConflict] rather
     * than guessed at (Constitution: never silently lose data). */
    data class EditDeleteConflict(
        val localSummary: String,
        val remoteSummary: String,
    ) : ApplyOutcome
}

/** Resolves another table's [syncId] to this device's own local row id —
 * implemented by [SyncEngine] (which has every DAO), passed into each
 * handler so an incoming `garments.categoryId` (sent as the category's
 * syncId, never its sender-local numeric id) resolves to *this* device's
 * numeric id for that same category, which will usually differ from the
 * sender's. See `phase-8-multi-device-sync.md`'s "Why not UUID primary
 * keys" section. */
fun interface SyncIdResolver {
    suspend fun resolveLocalId(
        tableName: String,
        syncId: String,
    ): Long?
}

/**
 * One per independently-tracked table (`SyncEntityType` in `core:model`).
 * Every handler is table-specific on purpose — the fields, foreign keys, and
 * "collection" cross-refs each aggregate carries are different enough that
 * a fully generic reflection-based version would need almost as much
 * per-table metadata as just writing the handler directly, with far less
 * clarity. See `phase-8-multi-device-sync.md`'s "Entity handlers" section.
 */
interface SyncEntityHandler {
    val tableName: String

    /** The current full row for [syncId], serialized with every foreign key
     * already translated to the referenced row's own syncId — or `null` if
     * this device no longer has (or never had) that row. */
    suspend fun currentFieldsJson(syncId: String): String?

    suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome

    suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome
}
