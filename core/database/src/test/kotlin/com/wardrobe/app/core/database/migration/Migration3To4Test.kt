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

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/3.json"

/**
 * Verifies v3 → v4 (Phase 7's five additive `weather_cache` columns) — same
 * "build the prior version straight from its committed schema JSON, not
 * `MigrationTestHelper`" approach [Migration1To2Test]/[Migration2To3Test]
 * use, and for the same reason (see [Migration1To2Test]'s own KDoc and
 * `TECHNICAL_DEBT.md`). Also needs [MIGRATION_4_5] in its own migrations
 * list now, for the same "Room validates the full path to the class's
 * current declared version" reason — `WardrobeDatabase.version` moved to 5
 * in Phase 8.
 */
@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {
    @Test
    fun `migrate 3 to 4 preserves existing weather rows and adds the new nullable columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-3-4-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV3Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb
            .query(
                "SELECT latitude, tempHighC, currentTempC, feelsLikeC, humidityPercent, uvIndex, condition " +
                    "FROM weather_cache WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(51.5, cursor.getDouble(0), 0.0001)
                assertEquals(20.0, cursor.getDouble(1), 0.0001)
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
            }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV3Database(
        context: Context,
        dbFile: java.io.File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v3.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v3.execSQL(indexSql)
                }
            }
        }
        v3.execSQL(
            "INSERT INTO weather_cache " +
                "(id, latitude, longitude, date, fetchedAt, tempHighC, tempLowC, apparentTempHighC, " +
                "apparentTempLowC, precipitationProbabilityPercent, windSpeedKph, conditionCode) " +
                "VALUES (1, 51.5, -0.1, '2026-08-02', 1000, 20.0, 12.0, 19.0, 11.0, 30, 15.0, '1')",
        )
        v3.version = 3
        v3.close()
    }
}
