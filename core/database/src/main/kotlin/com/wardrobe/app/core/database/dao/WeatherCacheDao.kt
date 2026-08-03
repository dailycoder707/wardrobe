package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.WeatherCacheEntity

/** Coordinates are rounded to ~2 decimal places (~1.1km) by the repository layer
 * (Phase 5a) before every read/write here — this DAO stores whatever it's given, see
 * phase-3-persistence.md. */
@Dao
interface WeatherCacheDao {
    @Query(
        "SELECT * FROM weather_cache WHERE latitude = :latitude AND longitude = :longitude AND date = :date",
    )
    suspend fun get(
        latitude: Double,
        longitude: Double,
        date: String,
    ): WeatherCacheEntity?

    @Query(
        """
        SELECT * FROM weather_cache
        WHERE latitude = :latitude AND longitude = :longitude
        ORDER BY fetchedAt DESC LIMIT 1
        """,
    )
    suspend fun getMostRecent(
        latitude: Double,
        longitude: Double,
    ): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity): Long

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)
}
