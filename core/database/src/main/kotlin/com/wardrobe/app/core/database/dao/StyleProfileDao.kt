package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.StyleProfileAvoidedCategoryCrossRef
import com.wardrobe.app.core.database.entity.StyleProfilePreferredBrandCrossRef
import kotlinx.coroutines.flow.Flow

/** Only the relational parts of the style profile — scalar fields live in DataStore
 * (`core:datastore`), see phase-3-persistence.md. */
@Dao
interface StyleProfileDao {
    @Query("SELECT * FROM style_profile_preferred_brands")
    fun observePreferredBrands(): Flow<List<StyleProfilePreferredBrandCrossRef>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPreferredBrand(entry: StyleProfilePreferredBrandCrossRef)

    @Query("DELETE FROM style_profile_preferred_brands WHERE brandId = :brandId")
    suspend fun removePreferredBrand(brandId: Long)

    @Query("SELECT * FROM style_profile_avoided_categories")
    fun observeAvoidedCategories(): Flow<List<StyleProfileAvoidedCategoryCrossRef>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAvoidedCategory(entry: StyleProfileAvoidedCategoryCrossRef)

    @Query("DELETE FROM style_profile_avoided_categories WHERE categoryId = :categoryId")
    suspend fun removeAvoidedCategory(categoryId: Long)
}
