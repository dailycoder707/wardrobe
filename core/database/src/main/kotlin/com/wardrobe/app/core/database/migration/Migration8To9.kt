package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v8 → v9 — near-automatic Add-to-Wardrobe (AI Wardrobe Assistant Parts
 * 1-3). Four additive `garments` columns (`secondaryColorId`, `neckline`,
 * `gender`, `waterproofLevel` — all nullable, no default needed, matching
 * every prior additive-column migration's shape) plus three new tables:
 * `fabrics` (weave/construction reference data, mirrors `materials`
 * exactly — deliberately distinct from fiber-content `Material`, see
 * `Fabric`'s own KDoc), `garment_fabrics` (cross-ref, mirrors
 * `garment_materials`), and `garment_occasions` (cross-ref reusing the
 * *existing* `occasions` table already used by `Outfit` — no new
 * reference table needed for occasion itself). No existing table's
 * existing columns are altered or removed.
 *
 * Also backfills starter rows into `materials` (previously seeded
 * nowhere at all — a real, pre-existing gap that would otherwise
 * permanently block AI-suggested-material auto-fill, see
 * `WardrobeDatabase.SeedCallback.seedDefaultMaterialsAndFabrics`'s KDoc)
 * and the new `fabrics` table, for installs *upgrading* through this
 * migration — `SeedCallback.onCreate` only ever runs for a brand-new
 * install, same precedent as `MIGRATION_6_7`'s subcategory backfill.
 * `INSERT OR IGNORE` against the unique `name` index means this never
 * duplicates or clobbers a name the user (or an earlier partial run)
 * already created.
 */
private const val SCHEMA_VERSION_8 = 8
private const val SCHEMA_VERSION_9 = 9

private val DEFAULT_MATERIALS =
    listOf(
        "Cotton",
        "Polyester",
        "Wool",
        "Linen",
        "Silk",
        "Nylon",
        "Spandex",
        "Rayon",
        "Cashmere",
        "Leather",
        "Suede",
        "Acrylic",
    )

private val DEFAULT_FABRICS =
    listOf(
        "Denim",
        "Jersey",
        "Twill",
        "Flannel",
        "Fleece",
        "Satin",
        "Chiffon",
        "Canvas",
        "Corduroy",
        "Oxford",
        "Poplin",
    )

val MIGRATION_8_9 =
    object : Migration(SCHEMA_VERSION_8, SCHEMA_VERSION_9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addGarmentColumns(db)
            createFabricsTable(db)
            createGarmentFabricsTable(db)
            createGarmentOccasionsTable(db)
            backfillMaterials(db)
            backfillFabrics(db)
        }
    }

private fun backfillMaterials(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    DEFAULT_MATERIALS.forEach { name ->
        db.execSQL(
            "INSERT OR IGNORE INTO materials (name, syncId, updatedAt) VALUES (?, ?, ?)",
            arrayOf<Any>(name, UUID.randomUUID().toString(), now),
        )
    }
}

private fun backfillFabrics(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    DEFAULT_FABRICS.forEach { name ->
        db.execSQL(
            "INSERT OR IGNORE INTO fabrics (name, syncId, updatedAt) VALUES (?, ?, ?)",
            arrayOf<Any>(name, UUID.randomUUID().toString(), now),
        )
    }
}

private fun addGarmentColumns(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        ALTER TABLE garments ADD COLUMN secondaryColorId INTEGER
        REFERENCES colors(id) ON UPDATE NO ACTION ON DELETE SET NULL
        """.trimIndent(),
    )
    db.execSQL("ALTER TABLE garments ADD COLUMN neckline TEXT")
    db.execSQL("ALTER TABLE garments ADD COLUMN gender TEXT")
    db.execSQL("ALTER TABLE garments ADD COLUMN waterproofLevel TEXT")
}

private fun createFabricsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `fabrics` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `syncId` TEXT NOT NULL DEFAULT '',
            `updatedAt` INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fabrics_name` ON `fabrics` (`name`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fabrics_syncId` ON `fabrics` (`syncId`)")
}

private fun createGarmentFabricsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `garment_fabrics` (
            `garmentId` INTEGER NOT NULL,
            `fabricId` INTEGER NOT NULL,
            `percentage` INTEGER,
            PRIMARY KEY(`garmentId`, `fabricId`),
            FOREIGN KEY(`garmentId`) REFERENCES `garments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`fabricId`) REFERENCES `fabrics`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
}

private fun createGarmentOccasionsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `garment_occasions` (
            `garmentId` INTEGER NOT NULL,
            `occasionId` INTEGER NOT NULL,
            PRIMARY KEY(`garmentId`, `occasionId`),
            FOREIGN KEY(`garmentId`) REFERENCES `garments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`occasionId`) REFERENCES `occasions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
}
