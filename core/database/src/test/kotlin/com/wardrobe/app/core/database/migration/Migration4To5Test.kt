package com.wardrobe.app.core.database.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.database.WardrobeDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val SCHEMA_ASSET_PATH = "com.wardrobe.app.core.database.WardrobeDatabase/4.json"

/**
 * Verifies v4 → v5 (Phase 8's `syncId`/`updatedAt` backfill plus the sync
 * outbox triggers) — same "build the prior version straight from its
 * committed schema JSON" approach [Migration3To4Test] uses.
 */
@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {
    @Test
    fun `migrate 4 to 5 backfills distinct syncIds and a real updatedAt for every existing row`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-4-5-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV4Database(context, dbFile)

        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        val syncIds = mutableListOf<String>()
        supportDb.query("SELECT name, syncId, updatedAt FROM tags ORDER BY name").use { cursor ->
            assertTrue(cursor.moveToFirst())
            while (!cursor.isAfterLast) {
                val syncId = cursor.getString(1)
                assertTrue("syncId must be non-blank", syncId.isNotBlank())
                assertTrue("updatedAt must be backfilled", cursor.getLong(2) > 0)
                syncIds.add(syncId)
                cursor.moveToNext()
            }
        }
        assertEquals(2, syncIds.size)
        assertNotEquals("two rows must not share a backfilled syncId", syncIds[0], syncIds[1])

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `inserting a new tag after migration writes a sync_change_log outbox row`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-4-5-trigger-test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        buildV4Database(context, dbFile)
        val migratedDb =
            Room
                .databaseBuilder(context, WardrobeDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        val supportDb = migratedDb.openHelper.writableDatabase

        supportDb.execSQL("INSERT INTO tags (name, syncId, updatedAt) VALUES ('Vacation', 'a-new-sync-id', 12345)")

        supportDb
            .query(
                "SELECT operation, syncId FROM sync_change_log WHERE tableName = 'tags' AND syncId = 'a-new-sync-id'",
            ).use { cursor ->
                assertTrue("the insert trigger must have logged this row", cursor.moveToFirst())
                assertEquals("INSERT", cursor.getString(0))
            }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private fun buildV4Database(
        context: Context,
        dbFile: File,
    ) {
        val schemaJson =
            context.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entities = JSONObject(schemaJson).getJSONObject("database").getJSONArray("entities")

        val v4 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            v4.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val indexSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    v4.execSQL(indexSql)
                }
            }
        }
        v4.execSQL("INSERT INTO tags (name) VALUES ('Formal')")
        v4.execSQL("INSERT INTO tags (name) VALUES ('Casual')")
        v4.version = 4
        v4.close()
    }
}
