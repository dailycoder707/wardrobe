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

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/2.json"

/**
 * Verifies v2 → v3 (Phase 6's one additive `garments.isInLaundry` column) —
 * same "build the prior version straight from its committed schema JSON,
 * not `MigrationTestHelper`" approach [Migration1To2Test] uses, and for the
 * same reason (see that test's own KDoc and `TECHNICAL_DEBT.md`).
 *
 * Also needs [MIGRATION_3_4]/[MIGRATION_4_5] in its own migrations list now,
 * for the same "Room validates the full path to the class's current
 * declared version" reason [Migration1To2Test] documents —
 * `WardrobeDatabase.version` moved to 5 in Phase 8.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {
    @Test
    fun `migrate 2 to 3 preserves existing garments and defaults isInLaundry to false`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-2-3-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV2Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb.query("SELECT name, isInLaundry FROM garments WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Blue Shirt", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV2Database(
        context: Context,
        dbFile: java.io.File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v2 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v2.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v2.execSQL(indexSql)
                }
            }
        }
        v2.execSQL(
            "INSERT INTO categories (id, name, parentId, level) VALUES (1, 'Tops', NULL, 'TOP')",
        )
        v2.execSQL(
            "INSERT INTO garments " +
                "(id, name, categoryId, primaryColorId, pattern, fit, length, sleeveLength, warmthRating, " +
                "breathabilityRating, brandId, size, price, currencyCode, purchaseDate, condition, careNotes, " +
                "status, isReviewed, isFavorite, searchText, createdAt, updatedAt) " +
                "VALUES (1, 'Blue Shirt', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, " +
                "NULL, NULL, NULL, 'ACTIVE', 1, 0, 'blue shirt', 1000, 1000)",
        )
        v2.version = 2
        v2.close()
    }
}
