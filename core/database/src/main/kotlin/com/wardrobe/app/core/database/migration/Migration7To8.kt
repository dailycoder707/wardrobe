package com.wardrobe.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8 — Add-to-Wardrobe v2 / the unified AI provider architecture
 * (ADR-012). Three new, device-local-only tables (no `syncId`, same
 * exclusion precedent as `import_queue_items`): `ai_jobs` (`AiJobManager`'s
 * generalized job ledger), `ai_result_cache` (the multi-stage cache *and*
 * provenance ledger), `ai_call_log` (cost/metrics). No existing table is
 * altered.
 */
private const val SCHEMA_VERSION_7 = 7
private const val SCHEMA_VERSION_8 = 8

val MIGRATION_7_8 =
    object : Migration(SCHEMA_VERSION_7, SCHEMA_VERSION_8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createAiJobsTable(db)
            createAiResultCacheTable(db)
            createAiCallLogTable(db)
        }
    }

private fun createAiJobsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `ai_jobs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `capability` TEXT NOT NULL,
            `cacheKey` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `errorMessage` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_jobs_status` ON `ai_jobs` (`status`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_jobs_cacheKey` ON `ai_jobs` (`cacheKey`)")
}

private fun createAiResultCacheTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `ai_result_cache` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `cacheKey` TEXT NOT NULL,
            `capability` TEXT NOT NULL,
            `provider` TEXT,
            `model` TEXT,
            `promptVersion` TEXT,
            `source` TEXT NOT NULL,
            `resultPayloadJson` TEXT NOT NULL,
            `confidence` REAL,
            `generatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_result_cache_cacheKey` ON `ai_result_cache` (`cacheKey`)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_ai_result_cache_capability` ON `ai_result_cache` (`capability`)",
    )
}

private fun createAiCallLogTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `ai_call_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `capability` TEXT NOT NULL,
            `provider` TEXT,
            `model` TEXT,
            `latencyMs` INTEGER NOT NULL,
            `outcome` TEXT NOT NULL,
            `confidence` REAL,
            `estimatedInputTokens` INTEGER,
            `estimatedOutputTokens` INTEGER,
            `estimatedCostMinorUnits` INTEGER,
            `cacheHit` INTEGER NOT NULL,
            `timestamp` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_call_log_capability` ON `ai_call_log` (`capability`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_call_log_timestamp` ON `ai_call_log` (`timestamp`)")
}
