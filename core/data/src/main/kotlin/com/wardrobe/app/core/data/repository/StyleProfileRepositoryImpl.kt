package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.StyleProfileDao
import com.wardrobe.app.core.database.entity.StyleProfileAvoidedCategoryCrossRef
import com.wardrobe.app.core.database.entity.StyleProfilePreferredBrandCrossRef
import com.wardrobe.app.core.datastore.preferences.StyleProfileDataStore
import com.wardrobe.app.core.domain.repository.StyleProfileRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.profile.GenderPreference
import com.wardrobe.app.core.model.profile.StyleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Composes the DataStore-backed scalars with the two Room junction tables into one
 * [StyleProfile] — see phase-3-persistence.md for why the profile is split across
 * two stores in the first place. */
class StyleProfileRepositoryImpl
    @Inject
    constructor(
        private val dataStore: StyleProfileDataStore,
        private val dao: StyleProfileDao,
    ) : StyleProfileRepository {
        override fun observeProfile(): Flow<StyleProfile> =
            combine(dataStore.observe(), dao.observePreferredBrands(), dao.observeAvoidedCategories()) {
                scalars,
                brands,
                categories,
                ->
                StyleProfile(
                    occupation = scalars.occupation,
                    genderPreference = scalars.genderPreference,
                    preferenceBlurb = scalars.preferenceBlurb,
                    budgetMin = scalars.budgetMin,
                    budgetMax = scalars.budgetMax,
                    preferredBrandIds = brands.map { BrandId(it.brandId) }.toSet(),
                    avoidedCategoryIds = categories.map { CategoryId(it.categoryId) }.toSet(),
                )
            }

        override suspend fun updateScalars(
            occupation: String?,
            genderPreference: GenderPreference?,
            preferenceBlurb: String?,
        ) = dataStore.updateScalars(occupation, genderPreference, preferenceBlurb)

        override suspend fun updateBudget(
            min: Money?,
            max: Money?,
        ) = dataStore.updateBudget(min, max)

        override suspend fun addPreferredBrand(brandId: BrandId) =
            dao.addPreferredBrand(StyleProfilePreferredBrandCrossRef(brandId.value))

        override suspend fun removePreferredBrand(brandId: BrandId) = dao.removePreferredBrand(brandId.value)

        override suspend fun addAvoidedCategory(categoryId: CategoryId) =
            dao.addAvoidedCategory(StyleProfileAvoidedCategoryCrossRef(categoryId.value))

        override suspend fun removeAvoidedCategory(categoryId: CategoryId) = dao.removeAvoidedCategory(categoryId.value)
    }
