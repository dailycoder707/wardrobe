package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.profile.GenderPreference
import com.wardrobe.app.core.model.profile.StyleProfile
import kotlinx.coroutines.flow.Flow

/** Composes scalar fields (DataStore) and relational preferences (Room junction
 * tables) into one [StyleProfile] — see phase-3-persistence.md. */
interface StyleProfileRepository {
    fun observeProfile(): Flow<StyleProfile>

    suspend fun updateScalars(
        occupation: String?,
        genderPreference: GenderPreference?,
        preferenceBlurb: String?,
    )

    /** Added during Phase 5a implementation — Phase 3's original interface had no
     * setter for the budget fields it defined on [StyleProfile] itself, a real gap
     * rather than a deliberate omission. */
    suspend fun updateBudget(
        min: Money?,
        max: Money?,
    )

    suspend fun addPreferredBrand(brandId: BrandId)

    suspend fun removePreferredBrand(brandId: BrandId)

    suspend fun addAvoidedCategory(categoryId: CategoryId)

    suspend fun removeAvoidedCategory(categoryId: CategoryId)
}
