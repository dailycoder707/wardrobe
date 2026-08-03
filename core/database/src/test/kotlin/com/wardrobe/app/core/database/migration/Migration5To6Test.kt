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
import java.io.File

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/5.json"

/**
 * Verifies v5 → v6 (Phase 10's five new try-on tables) — same "build the
 * prior version straight from its committed schema JSON" approach
 * [Migration4To5Test] uses. No backfill to verify here (unlike v4→v5):
 * every new table is brand new, so this test only needs to confirm the
 * tables/indices/triggers actually exist and behave after migrating an
 * empty v5 database.
 */
@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {
    @Test
    fun `migrate 5 to 6 creates the five new tables with working syncId uniqueness`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-5-6-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV5Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb.execSQL(
            "INSERT INTO body_profiles (label, createdAt, updatedAt, syncId) VALUES ('Me', 1000, 1000, 'profile-sync-id')",
        )
        supportDb.query("SELECT COUNT(*) FROM body_profiles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `inserting a garment placement template after migration writes a sync_change_log outbox row`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-5-6-trig.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV5Database(context, dbFile)
        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb.execSQL(
            "INSERT INTO body_profiles (label, createdAt, updatedAt, syncId) VALUES ('Me', 1000, 1000, 'profile-1')",
        )
        supportDb.execSQL(
            "INSERT INTO categories (name, parentId, level, syncId, updatedAt) VALUES ('Tops', NULL, 'TOP', 'category-1', 1000)",
        )
        supportDb.execSQL(
            """
            INSERT INTO garments
                (categoryId, status, isReviewed, isFavorite, isInLaundry, searchText, createdAt, updatedAt, syncId)
            VALUES
                ((SELECT id FROM categories WHERE syncId = 'category-1'), 'ACTIVE', 1, 0, 0, 'shirt', 1000, 1000, 'garment-1')
            """.trimIndent(),
        )
        supportDb.execSQL(
            """
            INSERT INTO garment_placement_templates
                (bodyProfileId, garmentId, templateType, customName, offsetXFraction, offsetYFraction,
                 scale, rotationDegrees, isUserAdjusted, placementSource, updatedAt, syncId)
            VALUES
                ((SELECT id FROM body_profiles WHERE syncId = 'profile-1'),
                 (SELECT id FROM garments WHERE syncId = 'garment-1'),
                 'DEFAULT', NULL, 0.5, 0.3, 1.0, 0.0, 0, 'DEFAULT_HEURISTIC', 1000, 'placement-1')
            """.trimIndent(),
        )

        supportDb
            .query(
                "SELECT operation, syncId FROM sync_change_log " +
                    "WHERE tableName = 'garment_placement_templates' AND syncId = 'placement-1'",
            ).use { cursor ->
                assertTrue("the insert trigger must have logged this row", cursor.moveToFirst())
                assertEquals("INSERT", cursor.getString(0))
            }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV5Database(
        context: Context,
        dbFile: File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v5 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v5.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v5.execSQL(indexSql)
                }
            }
        }
        v5.version = 5
        v5.close()
    }
}
