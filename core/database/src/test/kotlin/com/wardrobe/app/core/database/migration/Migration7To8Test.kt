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

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/7.json"

/**
 * Verifies v7 → v8 (Add-to-Wardrobe v2 / the unified AI provider
 * architecture, ADR-012) — same "build the prior version straight from its
 * committed schema JSON" approach [Migration6To7Test] uses.
 */
@RunWith(RobolectricTestRunner::class)
class Migration7To8Test {
    @Test
    fun `migrate 7 to 8 creates a working ai_jobs table`() {
        migrateFromV7(dbName = "migration-7-8-jobs.db") { supportDb ->
            supportDb.execSQL(
                """
                INSERT INTO ai_jobs (capability, cacheKey, status, errorMessage, createdAt, updatedAt)
                VALUES ('GARMENT_METADATA', 'cache-key-1', 'PENDING', NULL, 1000, 1000)
                """.trimIndent(),
            )
            supportDb.query("SELECT status FROM ai_jobs WHERE cacheKey = 'cache-key-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING", cursor.getString(0))
            }
        }
    }

    @Test
    fun `migrate 7 to 8 creates a working ai_result_cache table with a unique cacheKey`() {
        migrateFromV7(dbName = "migration-7-8-cache.db") { supportDb ->
            supportDb.execSQL(
                """
                INSERT INTO ai_result_cache
                    (cacheKey, capability, provider, model, promptVersion, source, resultPayloadJson, confidence, generatedAt)
                VALUES
                    ('cache-key-2', 'GARMENT_METADATA', 'openai', 'gpt-vision', 'metadata-v1', 'CLOUD', '{}', 0.9, 2000)
                """.trimIndent(),
            )
            supportDb.query("SELECT source FROM ai_result_cache WHERE cacheKey = 'cache-key-2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CLOUD", cursor.getString(0))
            }
            assertThrowsOnDuplicateCacheKey(supportDb)
        }
    }

    @Test
    fun `migrate 7 to 8 creates a working ai_call_log table with nullable cost fields`() {
        migrateFromV7(dbName = "migration-7-8-log.db") { supportDb ->
            supportDb.execSQL(
                """
                INSERT INTO ai_call_log
                    (capability, provider, model, latencyMs, outcome, confidence,
                     estimatedInputTokens, estimatedOutputTokens, estimatedCostMinorUnits, cacheHit, timestamp)
                VALUES
                    ('GARMENT_EXTRACTION', NULL, NULL, 250, 'CACHE_HIT', NULL, NULL, NULL, NULL, 1, 3000)
                """.trimIndent(),
            )
            supportDb.query("SELECT estimatedCostMinorUnits FROM ai_call_log WHERE timestamp = 3000").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
        }
    }

    private fun assertThrowsOnDuplicateCacheKey(supportDb: SupportSQLiteDatabase) {
        var threw = false
        try {
            supportDb.execSQL(
                """
                INSERT INTO ai_result_cache
                    (cacheKey, capability, provider, model, promptVersion, source, resultPayloadJson, confidence, generatedAt)
                VALUES
                    ('cache-key-2', 'GARMENT_METADATA', 'openai', 'gpt-vision', 'metadata-v1', 'CLOUD', '{}', 0.9, 2001)
                """.trimIndent(),
            )
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("expected the unique cacheKey index to reject a duplicate insert", threw)
    }

    private fun migrateFromV7(
        dbName: String,
        assertions: (SupportSQLiteDatabase) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV7Database(context, dbFile)

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

    private fun buildV7Database(
        context: Context,
        dbFile: File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v7 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v7.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v7.execSQL(indexSql)
                }
            }
        }
        v7.version = 7
        v7.close()
    }
}
