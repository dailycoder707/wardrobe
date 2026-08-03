package com.wardrobe.app.core.database.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/6.json"

/** The same 13 top-level buckets [WardrobeDatabase.SeedCallback] seeds on a
 * real fresh install — the schema-JSON-only v6 database this test builds has
 * empty tables, so these must be inserted explicitly to reproduce what a
 * real upgrading install actually looks like before `MIGRATION_6_7` runs. */
private val SEEDED_TOP_LEVEL_BUCKETS =
    listOf(
        "Tops",
        "Bottoms",
        "Dresses",
        "Jumpsuits",
        "Outerwear",
        "Shoes",
        "Bags",
        "Jewelry",
        "Belts",
        "Scarves",
        "Hair Accessories",
        "Sunglasses",
        "Other Accessories",
    )

/**
 * Verifies v6 → v7 (the Add-to-Wardrobe ingestion fix) — same "build the
 * prior version straight from its committed schema JSON" approach
 * [Migration5To6Test] uses.
 */
@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {
    @Test
    fun `migrate 6 to 7 adds a working notes column to garments`() {
        migrateFromV6(dbName = "migration-6-7-notes.db") { supportDb ->
            supportDb.execSQL(
                """
                INSERT INTO garments
                    (categoryId, status, isReviewed, isFavorite, isInLaundry, searchText, createdAt, updatedAt, syncId, notes)
                VALUES
                    ((SELECT id FROM categories WHERE name = 'Tops' AND parentId IS NULL),
                     'ACTIVE', 1, 0, 0, 'shirt', 1000, 1000, 'garment-1', 'a note')
                """.trimIndent(),
            )
            supportDb.query("SELECT notes FROM garments WHERE syncId = 'garment-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a note", cursor.getString(0))
            }
        }
    }

    @Test
    fun `pre-existing garments have a null notes column after migration`() {
        migrateFromV6(
            dbName = "migration-6-7-pre-existing.db",
            beforeMigrate = { v6db ->
                v6db.execSQL(
                    """
                    INSERT INTO garments
                        (categoryId, status, isReviewed, isFavorite, isInLaundry, searchText, createdAt, updatedAt, syncId)
                    VALUES
                        ((SELECT id FROM categories WHERE name = 'Tops' AND parentId IS NULL),
                         'ACTIVE', 1, 0, 0, 'shirt', 1000, 1000, 'garment-pre')
                    """.trimIndent(),
                )
            },
        ) { supportDb ->
            supportDb.query("SELECT notes FROM garments WHERE syncId = 'garment-pre'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
        }
    }

    @Test
    fun `migrate 6 to 7 seeds new subcategories under existing top-level buckets`() {
        migrateFromV6(dbName = "migration-6-7-subcats.db") { supportDb ->
            assertSubcategoryParentedUnder(supportDb, subName = "T-Shirts", topBucketName = "Tops")
            assertSubcategoryParentedUnder(supportDb, subName = "Wallet", topBucketName = "Bags")
            assertSubcategoryParentedUnder(supportDb, subName = "Nose Ring", topBucketName = "Jewelry")
            assertSubcategoryParentedUnder(supportDb, subName = "Cap", topBucketName = "Other Accessories")
        }
    }

    @Test
    fun `migrate 6 to 7 does not crash when an expected top-level bucket was renamed away`() {
        migrateFromV6(
            dbName = "migration-6-7-renamed.db",
            beforeMigrate = { v6db ->
                v6db.execSQL("UPDATE categories SET name = 'Tops (renamed)' WHERE name = 'Tops' AND parentId IS NULL")
            },
        ) { supportDb ->
            supportDb.query("SELECT COUNT(*) FROM categories WHERE name = 'T-Shirts'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            // Buckets that weren't renamed still seed normally.
            assertSubcategoryParentedUnder(supportDb, subName = "Wallet", topBucketName = "Bags")
        }
    }

    @Test
    fun `migrate 6 to 7 creates a working import_queue_items table`() {
        migrateFromV6(dbName = "migration-6-7-queue.db") { supportDb ->
            supportDb.execSQL(
                """
                INSERT INTO import_queue_items
                    (sourceFilePath, stagingId, status, errorMessage, savedGarmentId, createdAt, updatedAt)
                VALUES
                    ('/tmp/photo.jpg', NULL, 'PENDING', NULL, NULL, 1000, 1000)
                """.trimIndent(),
            )
            supportDb
                .query("SELECT status FROM import_queue_items WHERE sourceFilePath = '/tmp/photo.jpg'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("PENDING", cursor.getString(0))
                }
        }
    }

    private fun assertSubcategoryParentedUnder(
        supportDb: SupportSQLiteDatabase,
        subName: String,
        topBucketName: String,
    ) {
        supportDb
            .query(
                "SELECT COUNT(*) FROM categories WHERE name = ? " +
                    "AND parentId = (SELECT id FROM categories WHERE name = ? AND parentId IS NULL)",
                arrayOf<Any>(subName, topBucketName),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("expected exactly one '$subName' under '$topBucketName'", 1, cursor.getInt(0))
            }
    }

    private fun migrateFromV6(
        dbName: String = "migration-6-7-test.db",
        beforeMigrate: (SQLiteDatabase) -> Unit = {},
        assertions: (SupportSQLiteDatabase) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV6Database(context, dbFile, beforeMigrate)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        assertions(migratedDb.openHelper.writableDatabase)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV6Database(
        context: Context,
        dbFile: File,
        beforeMigrate: (SQLiteDatabase) -> Unit,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v6 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v6.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v6.execSQL(indexSql)
                }
            }
        }
        seedTopLevelCategories(v6)
        beforeMigrate(v6)
        v6.version = 6
        v6.close()
    }

    private fun seedTopLevelCategories(v6: SQLiteDatabase) {
        SEEDED_TOP_LEVEL_BUCKETS.forEachIndexed { index, name ->
            v6.execSQL(
                "INSERT INTO categories (name, parentId, level, syncId, updatedAt) VALUES (?, NULL, 'TOP', ?, 1000)",
                arrayOf<Any>(name, "top-bucket-$index"),
            )
        }
    }
}
