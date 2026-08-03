package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6 — Phase 10's Personal Virtual Try-On. Five new tables, no changes
 * to any existing table:
 * - `body_profiles` — the single-per-device on-device body profile
 *   ([syncId]/`updatedAt` from creation, independently sync-tracked — real
 *   user setup effort worth syncing between the user's own paired devices,
 *   per Constitution rule 13/ADR-011's one narrow exception).
 * - `body_reference_photos`, `body_measurements` — child rows of a body
 *   profile, riding along inside its sync payload (no `syncId` of their
 *   own), the same "collections ride along with their parent" convention
 *   Phase 8 already established for e.g. `garment_seasons`.
 * - `garment_placement_templates` — named affine placement variants per
 *   `(bodyProfile, garment)`; independently sync-tracked.
 * - `garment_masks` — manual erase/restore masks per `(bodyProfile,
 *   garment)`; independently sync-tracked.
 *
 * Unlike `MIGRATION_4_5`, no backfill is needed: every new table is brand
 * new, so `syncId`/`updatedAt` are just ordinary `NOT NULL` columns from
 * the `CREATE TABLE` statement, not a retrofit onto existing rows.
 */
private const val SCHEMA_VERSION_5 = 5
private const val SCHEMA_VERSION_6 = 6

/** The three tables of this migration that are independently sync-tracked
 * — the same trigger pattern `MIGRATION_4_5` established for every other
 * syncable table. */
private val NEW_SYNCABLE_TABLES = listOf("body_profiles", "garment_placement_templates", "garment_masks")

val MIGRATION_5_6 =
    object : Migration(SCHEMA_VERSION_5, SCHEMA_VERSION_6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createBodyProfileTables(db)
            createPlacementAndMaskTables(db)
            NEW_SYNCABLE_TABLES.forEach { table -> createChangeLogTriggers(db, table) }
        }
    }

private fun createBodyProfileTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `body_profiles` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `label` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL DEFAULT 0,
            `syncId` TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_body_profiles_syncId` ON `body_profiles` (`syncId`)")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `body_reference_photos` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `bodyProfileId` INTEGER NOT NULL,
            `pose` TEXT NOT NULL,
            `filePath` TEXT NOT NULL,
            `width` INTEGER NOT NULL,
            `height` INTEGER NOT NULL,
            `checksum` TEXT,
            FOREIGN KEY(`bodyProfileId`) REFERENCES `body_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_body_reference_photos_bodyProfileId` " +
            "ON `body_reference_photos` (`bodyProfileId`)",
    )

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `body_measurements` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `bodyProfileId` INTEGER NOT NULL,
            `shoulderWidthFraction` REAL,
            `torsoHeightFraction` REAL,
            `waistHeightFraction` REAL,
            `hipWidthFraction` REAL,
            `neckPositionYFraction` REAL,
            `anklePositionYFraction` REAL,
            `confidence` REAL,
            `source` TEXT NOT NULL,
            `computedAt` INTEGER NOT NULL,
            FOREIGN KEY(`bodyProfileId`) REFERENCES `body_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_measurements_bodyProfileId` " +
            "ON `body_measurements` (`bodyProfileId`)",
    )
}

private fun createPlacementAndMaskTables(db: SupportSQLiteDatabase) {
    createPlacementTemplateTable(db)
    createMaskTable(db)
}

private fun createPlacementTemplateTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `garment_placement_templates` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `bodyProfileId` INTEGER NOT NULL,
            `garmentId` INTEGER NOT NULL,
            `templateType` TEXT NOT NULL,
            `customName` TEXT,
            `offsetXFraction` REAL NOT NULL,
            `offsetYFraction` REAL NOT NULL,
            `scale` REAL NOT NULL,
            `rotationDegrees` REAL NOT NULL,
            `isUserAdjusted` INTEGER NOT NULL,
            `placementSource` TEXT NOT NULL,
            `lastUsedAt` INTEGER,
            `updatedAt` INTEGER NOT NULL DEFAULT 0,
            `syncId` TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(`bodyProfileId`) REFERENCES `body_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`garmentId`) REFERENCES `garments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_garment_placement_templates_bodyProfileId` " +
            "ON `garment_placement_templates` (`bodyProfileId`)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_garment_placement_templates_garmentId` " +
            "ON `garment_placement_templates` (`garmentId`)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_garment_placement_templates_composite` " +
            "ON `garment_placement_templates` (`bodyProfileId`, `garmentId`, `templateType`, `customName`)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_garment_placement_templates_syncId` " +
            "ON `garment_placement_templates` (`syncId`)",
    )
}

private fun createMaskTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `garment_masks` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `bodyProfileId` INTEGER NOT NULL,
            `garmentId` INTEGER NOT NULL,
            `maskFilePath` TEXT NOT NULL,
            `checksum` TEXT,
            `updatedAt` INTEGER NOT NULL DEFAULT 0,
            `syncId` TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(`bodyProfileId`) REFERENCES `body_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`garmentId`) REFERENCES `garments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_garment_masks_composite` " +
            "ON `garment_masks` (`bodyProfileId`, `garmentId`)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_garment_masks_syncId` ON `garment_masks` (`syncId`)",
    )
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
