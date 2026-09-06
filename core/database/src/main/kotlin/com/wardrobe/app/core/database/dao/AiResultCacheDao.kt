package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.AiResultCacheEntity

@Dao
interface AiResultCacheDao {
    /** [OnConflictStrategy.REPLACE] on the unique `cacheKey` index — a
     * regenerated result for the same
     * `(imageSha256, capability, provider, model, promptVersion)` combination
     * (e.g. after a manual "regenerate") replaces the prior row rather than
     * erroring or duplicating. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiResultCacheEntity): Long

    @Query("SELECT * FROM ai_result_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): AiResultCacheEntity?

    @Query("DELETE FROM ai_result_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteByCacheKey(cacheKey: String)

    /** RC1 hardening: nothing ever called [deleteByCacheKey] in production
     * (only a manual "regenerate" would), so this table grew without bound —
     * a real, previously-unswept row-growth issue found during RC1's
     * resource audit, alongside the identical gap for the image-file cache
     * `deleteByCacheKey` doesn't touch either. `OrphanedImageCleanupWorker`
     * calls this on the same periodic cadence as its other sweeps. */
    @Query("DELETE FROM ai_result_cache WHERE generatedAt < :olderThanEpochMillis")
    suspend fun deleteOlderThan(olderThanEpochMillis: Long)
}
