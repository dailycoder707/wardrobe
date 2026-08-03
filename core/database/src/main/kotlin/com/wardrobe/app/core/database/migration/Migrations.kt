package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 — Phase 5d's Outfit Builder/Saved Looks metadata and Outfit
 * Scheduling. Every statement here must produce exactly the schema Room's
 * annotation processor expects for v2 (validated against
 * `core/database/schemas/.../2.json` at build time) — column/index names and
 * `NOT NULL`/`DEFAULT` clauses are written to match Room's own generated SQL
 * convention exactly (see the v1 schema JSON for the same convention), not
 * guessed at.
 *
 * Adds:
 * - `outfits.isFavorite`/`isArchived` (booleans, default false) and
 *   `notes`/`mood` (nullable text) — Outfit Builder metadata.
 * - `outfit_seasons`/`outfit_dress_codes`/`outfit_tags` — cross-ref tables
 *   mirroring `garment_seasons`/`garment_dress_codes`/`garment_tags` exactly.
 * - `wear_events.status` (default `'WORN'`, backfilling every existing row as
 *   an already-completed wear, which is what they all are) — the PLANNED/WORN
 *   split Outfit Scheduling needs; see `WearEventStatus`'s KDoc.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE outfits ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE outfits ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE outfits ADD COLUMN notes TEXT")
            db.execSQL("ALTER TABLE outfits ADD COLUMN mood TEXT")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outfit_seasons` (
                    `outfitId` INTEGER NOT NULL,
                    `season` TEXT NOT NULL,
                    PRIMARY KEY(`outfitId`, `season`),
                    FOREIGN KEY(`outfitId`) REFERENCES `outfits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_outfit_seasons_season` ON `outfit_seasons` (`season`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outfit_dress_codes` (
                    `outfitId` INTEGER NOT NULL,
                    `dressCode` TEXT NOT NULL,
                    PRIMARY KEY(`outfitId`, `dressCode`),
                    FOREIGN KEY(`outfitId`) REFERENCES `outfits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_outfit_dress_codes_dressCode` ON `outfit_dress_codes` (`dressCode`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outfit_tags` (
                    `outfitId` INTEGER NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    PRIMARY KEY(`outfitId`, `tagId`),
                    FOREIGN KEY(`outfitId`) REFERENCES `outfits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            db.execSQL("ALTER TABLE wear_events ADD COLUMN status TEXT NOT NULL DEFAULT 'WORN'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wear_events_status` ON `wear_events` (`status`)")
        }
    }

/**
 * v2 → v3 — Phase 6's styling engine needs one additive column: a manually-toggled
 * "don't recommend right now" flag, distinct from [com.wardrobe.app.core.model.garment.GarmentStatus]
 * (a permanent lifecycle state). No other schema change — the engine's remaining
 * inputs (favorites, wear history, dress code, season, trip packing lists) already
 * exist.
 */
private const val SCHEMA_VERSION_2 = 2
private const val SCHEMA_VERSION_3 = 3

val MIGRATION_2_3 =
    object : Migration(SCHEMA_VERSION_2, SCHEMA_VERSION_3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE garments ADD COLUMN isInLaundry INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * v3 → v4 — Phase 7's real `WeatherRepository` implementation needs five
 * additive columns on `weather_cache` beyond Phase 3's original daily-range
 * fields: the live "right now" reading (`currentTempC`/`feelsLikeC`/
 * `humidityPercent`/`uvIndex`) and a simple mapped `condition`, distinct from
 * the existing raw `conditionCode`. All nullable, no default needed — every
 * existing cached row simply has these as `NULL` until the next refresh
 * repopulates them.
 */
private const val SCHEMA_VERSION_4 = 4

val MIGRATION_3_4 =
    object : Migration(SCHEMA_VERSION_3, SCHEMA_VERSION_4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE weather_cache ADD COLUMN currentTempC REAL")
            db.execSQL("ALTER TABLE weather_cache ADD COLUMN feelsLikeC REAL")
            db.execSQL("ALTER TABLE weather_cache ADD COLUMN humidityPercent INTEGER")
            db.execSQL("ALTER TABLE weather_cache ADD COLUMN uvIndex REAL")
            db.execSQL("ALTER TABLE weather_cache ADD COLUMN condition TEXT")
        }
    }
