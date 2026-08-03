package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v6 → v7 — the Add-to-Wardrobe ingestion fix. Three concerns in one
 * migration:
 * - `garments.notes` — a new general free-text column, deliberately
 *   distinct from the pre-existing `careNotes` (care-instruction text
 *   specifically).
 * - ~37 new [CategoryEntity][com.wardrobe.app.core.database.entity.CategoryEntity]
 *   subcategory rows nesting under the 13 already-seeded top-level buckets
 *   (no new top-level categories) — see `seedNewSubcategories`'s mapping
 *   table below, which must stay in sync with [WardrobeDatabase.SeedCallback]'s
 *   own fresh-install seed list, since `onCreate` never runs for an
 *   upgrading install and this migration never runs for a fresh one.
 *   Each row's parent is resolved by querying the existing top-level row's
 *   `id` by name (mirroring [MIGRATION_4_5]'s `backfillSyncIds` cursor
 *   pattern) and is **skipped, not crashed on**, if that expected parent
 *   name isn't found — categories are fully user-editable, so a seeded
 *   top-level bucket may have been renamed or deleted before this runs.
 * - `import_queue_items` — the Room-backed Add-to-Wardrobe import queue.
 *   Deliberately **not** given a `syncId`/change-log trigger: an
 *   in-progress import on one device has no meaning on another, the same
 *   exclusion precedent as `weather_cache`/`stats_cache`.
 */
private const val SCHEMA_VERSION_6 = 6
private const val SCHEMA_VERSION_7 = 7

val MIGRATION_6_7 =
    object : Migration(SCHEMA_VERSION_6, SCHEMA_VERSION_7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE garments ADD COLUMN notes TEXT")
            seedNewSubcategories(db)
            createImportQueueTable(db)
        }
    }

/** `topBucketName to newSubcategoryNames` — must stay in sync with
 * [WardrobeDatabase.SeedCallback.seedDefaultCategories]'s fresh-install list. */
private val NEW_SUBCATEGORIES_BY_TOP_BUCKET =
    mapOf(
        "Tops" to listOf("T-Shirts", "Shirts", "Polo", "Tank Tops", "Hoodies", "Sweaters", "Kurtas"),
        "Outerwear" to listOf("Jackets", "Blazers", "Coats"),
        "Dresses" to listOf("Sarees"),
        "Bottoms" to listOf("Jeans", "Pants", "Shorts", "Skirts", "Leggings"),
        "Shoes" to listOf("Running Shoes", "Heels", "Flats", "Loafers", "Slippers"),
        "Bags" to listOf("Backpack", "Handbag", "Tote", "Laptop Bag", "Sling Bag", "Wallet"),
        "Jewelry" to listOf("Nose Ring", "Anklet"),
        "Other Accessories" to listOf("Cap", "Hat", "Gloves", "Socks", "Tie", "Bow Tie"),
        "Hair Accessories" to listOf("Hair Band", "Hair Clip"),
    )

private fun seedNewSubcategories(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    NEW_SUBCATEGORIES_BY_TOP_BUCKET.forEach { (topBucketName, subNames) ->
        val parentId = topLevelCategoryIdByName(db, topBucketName) ?: return@forEach
        subNames.forEach { subName ->
            db.execSQL(
                "INSERT INTO categories (name, parentId, level, syncId, updatedAt) VALUES (?, ?, 'SUB', ?, ?)",
                arrayOf<Any>(subName, parentId, UUID.randomUUID().toString(), now),
            )
        }
    }
}

private fun topLevelCategoryIdByName(
    db: SupportSQLiteDatabase,
    name: String,
): Long? {
    db
        .query(
            "SELECT id FROM categories WHERE name = ? AND parentId IS NULL",
            arrayOf<Any>(name),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
}

private fun createImportQueueTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `import_queue_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `sourceFilePath` TEXT NOT NULL,
            `stagingId` TEXT,
            `status` TEXT NOT NULL,
            `errorMessage` TEXT,
            `savedGarmentId` INTEGER,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_import_queue_items_status` ON `import_queue_items` (`status`)",
    )
}
