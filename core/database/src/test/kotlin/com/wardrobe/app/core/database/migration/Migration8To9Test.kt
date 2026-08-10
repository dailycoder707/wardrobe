package com.wardrobe.app.core.database.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/8.json"

/**
 * Verifies v8 → v9 (near-automatic Add-to-Wardrobe, AI Wardrobe Assistant
 * Parts 1-3) — same "build the prior version straight from its committed
 * schema JSON" approach [Migration7To8Test] uses.
 */
@RunWith(RobolectricTestRunner::class)
class Migration8To9Test {
    @Test
    fun `migrate 8 to 9 adds new garments columns that accept values`() {
        migrateFromV8(dbName = "migration-8-9-garment-columns.db") { supportDb ->
            insertMinimalGarment(supportDb)
            supportDb.execSQL(
                "INSERT INTO colors (id, name, hexValue, syncId, updatedAt) VALUES (5, 'Navy', '#000080', 'color-sync-1', 1000)",
            )
            supportDb.execSQL(
                """
                UPDATE garments
                SET secondaryColorId = 5, neckline = 'CREW', gender = 'UNISEX', waterproofLevel = 'WATERPROOF'
                WHERE id = 1
                """.trimIndent(),
            )
            supportDb
                .query(
                    "SELECT secondaryColorId, neckline, gender, waterproofLevel FROM garments WHERE id = 1",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(5, cursor.getInt(0))
                    assertEquals("CREW", cursor.getString(1))
                    assertEquals("UNISEX", cursor.getString(2))
                    assertEquals("WATERPROOF", cursor.getString(3))
                }
        }
    }

    @Test
    fun `migrate 8 to 9 creates a working fabrics table with a unique name`() {
        migrateFromV8(dbName = "migration-8-9-fabrics.db") { supportDb ->
            supportDb.execSQL(
                "INSERT INTO fabrics (name, syncId, updatedAt) VALUES ('Velvet', 'sync-1', 1000)",
            )
            supportDb.query("SELECT name FROM fabrics WHERE syncId = 'sync-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Velvet", cursor.getString(0))
            }
            var threw = false
            try {
                supportDb.execSQL(
                    "INSERT INTO fabrics (name, syncId, updatedAt) VALUES ('Velvet', 'sync-2', 2000)",
                )
            } catch (expected: android.database.sqlite.SQLiteConstraintException) {
                threw = true
            }
            assertTrue("expected the unique fabrics.name index to reject a duplicate insert", threw)
        }
    }

    @Test
    fun `migrate 8 to 9 backfills starter materials and fabrics`() {
        migrateFromV8(dbName = "migration-8-9-backfill.db") { supportDb ->
            supportDb.query("SELECT COUNT(*) FROM materials WHERE name = 'Cotton'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            supportDb.query("SELECT COUNT(*) FROM fabrics WHERE name = 'Denim'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `migrate 8 to 9 creates a working garment_fabrics cross-ref table`() {
        migrateFromV8(dbName = "migration-8-9-garment-fabrics.db") { supportDb ->
            insertMinimalGarment(supportDb)
            supportDb.query("SELECT id FROM fabrics WHERE name = 'Denim'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val fabricId = cursor.getLong(0)
                supportDb.execSQL(
                    "INSERT INTO garment_fabrics (garmentId, fabricId, percentage) VALUES (1, $fabricId, 100)",
                )
            }
            supportDb.query("SELECT percentage FROM garment_fabrics WHERE garmentId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(100, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `migrate 8 to 9 creates a working garment_occasions cross-ref table reusing occasions`() {
        migrateFromV8(dbName = "migration-8-9-occ.db") { supportDb ->
            insertMinimalGarment(supportDb)
            supportDb.execSQL(
                "INSERT INTO occasions (name, syncId, updatedAt) VALUES ('Casual', 'occ-sync-1', 1000)",
            )
            supportDb.query("SELECT id FROM occasions WHERE name = 'Casual'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val occasionId = cursor.getLong(0)
                supportDb.execSQL(
                    "INSERT INTO garment_occasions (garmentId, occasionId) VALUES (1, $occasionId)",
                )
            }
            supportDb.query("SELECT COUNT(*) FROM garment_occasions WHERE garmentId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private fun insertMinimalGarment(supportDb: SupportSQLiteDatabase) {
        supportDb.execSQL(
            """
            INSERT INTO categories (id, name, parentId, level, syncId, updatedAt)
            VALUES (1, 'Tops', NULL, 'TOP', 'category-sync-1', 1000)
            """.trimIndent(),
        )
        supportDb.execSQL(
            """
            INSERT INTO garments
                (id, categoryId, status, isReviewed, isFavorite, isInLaundry, searchText, syncId, createdAt, updatedAt)
            VALUES
                (1, 1, 'ACTIVE', 1, 0, 0, 'tops', 'garment-sync-1', 1000, 1000)
            """.trimIndent(),
        )
    }

    private fun migrateFromV8(
        dbName: String,
        assertions: (SupportSQLiteDatabase) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV8Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                ).build()
        assertions(migratedDb.openHelper.writableDatabase)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV8Database(
        context: Context,
        dbFile: File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v8 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v8.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v8.execSQL(indexSql)
                }
            }
        }
        v8.version = 8
        v8.close()
    }
}
