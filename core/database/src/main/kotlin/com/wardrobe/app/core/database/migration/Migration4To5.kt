package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v4 → v5 — Phase 8's multi-device sync. Every independently-tracked
 * ("aggregate root") table gains two columns:
 * - `syncId` (TEXT, UNIQUE) — a stable cross-device identity. Local primary
 *   keys are `Long AUTOINCREMENT`, which two offline devices will happily
 *   assign to *different* garments as the same number; `syncId` is what the
 *   wire protocol actually references, never the local `id`. See
 *   `phase-8-multi-device-sync.md`'s "Why not UUID primary keys" section.
 * - `updatedAt` (INTEGER, epoch millis) — needed for "newest modification
 *   wins" conflict resolution. `garments` already had this from Phase 3; the
 *   other 15 tables did not.
 *
 * A trigger per table (AFTER INSERT/UPDATE/DELETE) writes one row into the
 * new `sync_change_log` outbox — this is *database-level* change tracking,
 * deliberately not a per-repository-method hook: a hook can be forgotten at
 * a new call site, a trigger cannot. `garment_*`/`outfit_*` cross-ref
 * ("collection") tables are NOT given their own syncId/trigger — they ride
 * along inside their parent [com.wardrobe.app.core.database.entity.GarmentEntity]/
 * [com.wardrobe.app.core.database.entity.OutfitEntity] payload at sync time
 * and are merged as sets, per the brief's "Collections: Merge" rule.
 *
 * Four new tables: `paired_device` (the pairing registry + per-device sync
 * cursor), `sync_change_log` (the outbox), `sync_conflict` (surfaced
 * edit/delete conflicts), `sync_history` (the Sync History list).
 */
private const val SCHEMA_VERSION_4 = 4
private const val SCHEMA_VERSION_5 = 5

/** Tables that need a brand-new `updatedAt` column. `garments` already has
 * one (Phase 3) and is handled separately below. */
private val TABLES_NEEDING_UPDATED_AT =
    listOf(
        "categories",
        "colors",
        "materials",
        "brands",
        "tags",
        "occasions",
        "outfits",
        "wear_events",
        "style_rules",
        "feedback",
        "trips",
        "trip_activities",
        "packing_list_items",
        "wishlist_items",
        "image_metadata",
    )

/** Every independently sync-tracked table — see this file's own KDoc for why
 * cross-ref/collection tables are excluded. */
private val SYNCABLE_TABLES = TABLES_NEEDING_UPDATED_AT + "garments"

val MIGRATION_4_5 =
    object : Migration(SCHEMA_VERSION_4, SCHEMA_VERSION_5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            TABLES_NEEDING_UPDATED_AT.forEach { table ->
                db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE $table SET updatedAt = $now WHERE updatedAt = 0")
            }

            SYNCABLE_TABLES.forEach { table ->
                db.execSQL("ALTER TABLE $table ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                backfillSyncIds(db, table)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_syncId ON $table(syncId)",
                )
            }

            createSyncTables(db)
            SYNCABLE_TABLES.forEach { table -> createChangeLogTriggers(db, table) }
        }
    }

/** `ALTER TABLE ... ADD COLUMN` can only take a constant default (SQLite has
 * no `UUID()` function), so every pre-existing row starts with `syncId = ''`
 * and is backfilled one row at a time with a real, randomly-generated id —
 * a one-time cost paid once per install, on a personal wardrobe's row counts
 * (hundreds, not millions). */
private fun backfillSyncIds(
    db: SupportSQLiteDatabase,
    table: String,
) {
    val ids = mutableListOf<Long>()
    db.query("SELECT id FROM $table").use { cursor ->
        while (cursor.moveToNext()) ids.add(cursor.getLong(0))
    }
    ids.forEach { id ->
        db.execSQL(
            "UPDATE $table SET syncId = ? WHERE id = ?",
            arrayOf<Any>(UUID.randomUUID().toString(), id),
        )
    }
}

private fun createChangeLogTriggers(
    db: SupportSQLiteDatabase,
    table: String,
) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS trg_${table}_sync_insert AFTER INSERT ON $table
        BEGIN
            INSERT INTO sync_change_log(tableName, syncId, operation, changedAt)
            VALUES ('$table', NEW.syncId, 'INSERT', (CAST(strftime('%s', 'now') AS INTEGER) * 1000));
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS trg_${table}_sync_update AFTER UPDATE ON $table
        BEGIN
            INSERT INTO sync_change_log(tableName, syncId, operation, changedAt)
            VALUES ('$table', NEW.syncId, 'UPDATE', (CAST(strftime('%s', 'now') AS INTEGER) * 1000));
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS trg_${table}_sync_delete AFTER DELETE ON $table
        BEGIN
            INSERT INTO sync_change_log(tableName, syncId, operation, changedAt)
            VALUES ('$table', OLD.syncId, 'DELETE', (CAST(strftime('%s', 'now') AS INTEGER) * 1000));
        END
        """.trimIndent(),
    )
}

private fun createSyncTables(db: SupportSQLiteDatabase) {
    createSyncChangeLogTable(db)
    createPairedDeviceTable(db)
    createSyncConflictTable(db)
    createSyncHistoryTable(db)
}

private fun createSyncChangeLogTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_change_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `tableName` TEXT NOT NULL,
            `syncId` TEXT NOT NULL,
            `operation` TEXT NOT NULL,
            `changedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_sync_change_log_tableName_syncId` " +
            "ON `sync_change_log` (`tableName`, `syncId`)",
    )
}

private fun createPairedDeviceTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `paired_device` (
            `deviceId` TEXT PRIMARY KEY NOT NULL,
            `displayName` TEXT NOT NULL,
            `publicKeyFingerprint` TEXT NOT NULL,
            `publicKeyBase64` TEXT NOT NULL,
            `pairedAt` INTEGER NOT NULL,
            `lastSyncAt` INTEGER,
            `lastSyncedChangeLogId` INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
    )
}

private fun createSyncConflictTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_conflict` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `entityType` TEXT NOT NULL,
            `entitySyncId` TEXT NOT NULL,
            `reason` TEXT NOT NULL,
            `localSummary` TEXT NOT NULL,
            `remoteSummary` TEXT NOT NULL,
            `detectedAt` INTEGER NOT NULL,
            `resolvedAt` INTEGER,
            `resolution` TEXT
        )
        """.trimIndent(),
    )
}

private fun createSyncHistoryTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `finishedAt` INTEGER,
            `outcome` TEXT NOT NULL,
            `changesSent` INTEGER NOT NULL,
            `changesReceived` INTEGER NOT NULL,
            `bytesSent` INTEGER NOT NULL,
            `bytesReceived` INTEGER NOT NULL,
            `conflictsDetected` INTEGER NOT NULL,
            `errorMessage` TEXT
        )
        """.trimIndent(),
    )
}
