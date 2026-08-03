package com.wardrobe.app.core.database.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/1.json"

/**
 * Verifies the only real migration this schema has ever needed (v1 → v2,
 * Phase 5d's Outfit Builder/Scheduling additions), run under Robolectric
 * since no device/emulator exists in this environment.
 *
 * Deliberately does not use `androidx.room.testing.MigrationTestHelper`: Room
 * 2.8.4's new driver-based connection manager throws
 * `IllegalArgumentException: This driver is configured to open a database
 * named 'X' but '<absolute path>' was requested` when `MigrationTestHelper`
 * runs under Robolectric's per-test randomized data directory — a real,
 * empirically-hit environment/version interaction, not a mistake in this
 * test's own setup (see `TECHNICAL_DEBT.md`). Instead, this test builds the
 * v1 database directly from the exact schema committed at
 * `core/database/schemas/.../1.json` (every `CREATE TABLE`/`CREATE INDEX`
 * statement Room itself generated for v1, executed verbatim via the plain
 * framework `SQLiteDatabase` Robolectric shadows natively), seeds two rows,
 * then opens that file through the real `WardrobeDatabase` + [MIGRATION_1_2]
 * (plus [MIGRATION_2_3]/[MIGRATION_3_4]/[MIGRATION_4_5], required since
 * `WardrobeDatabase`'s declared version has since moved to 5 (Phase 8) —
 * Room validates the full migration path reaches the class's current
 * version, not just the one step this test cares about) and asserts both
 * the new columns/tables exist and the seeded data survived.
 */
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {
    @Test
    fun `migrate 1 to 2 preserves existing rows and adds outfit metadata and wear event status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV1Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb.query("SELECT name, isFavorite, isArchived, notes, mood FROM outfits WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Weekend Look", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }

        supportDb.query("SELECT status FROM wear_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WORN", cursor.getString(0))
        }

        supportDb.execSQL("INSERT INTO outfit_seasons (outfitId, season) VALUES (1, 'SUMMER')")
        supportDb.query("SELECT COUNT(*) FROM outfit_seasons WHERE outfitId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV1Database(
        context: Context,
        dbFile: java.io.File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v1.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v1.execSQL(indexSql)
                }
            }
        }
        v1.execSQL(
            "INSERT INTO outfits (id, name, occasionId, source, isSaved, photoUri, createdAt) " +
                "VALUES (1, 'Weekend Look', NULL, 'USER_CREATED', 1, NULL, 1000)",
        )
        v1.execSQL(
            "INSERT INTO wear_events " +
                "(id, date, garmentId, outfitId, weatherCacheId, occasionId, note, createdAt) " +
                "VALUES (1, '2026-01-01', NULL, 1, NULL, NULL, NULL, 1000)",
        )
        v1.version = 1
        v1.close()
    }
}
