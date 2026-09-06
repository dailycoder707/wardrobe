package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.OnboardingDataStore
import com.wardrobe.app.core.datastore.preferences.PersonalizationDataStore
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OnboardingRepository
import com.wardrobe.app.core.model.garment.GarmentFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * M16's first-run gate. Purely reactive, never a one-time destructive
 * migration/write: onboarding reads as complete if [OnboardingDataStore]'s
 * explicit flag says so, *or* if this device already shows real evidence of
 * prior use — a saved display name (`PersonalizationDataStore`, M15) or an
 * existing garment ([GarmentRepository]). Nothing is ever written back for
 * that second case; it's derived fresh every time, so there's no race, no
 * silent mutation, and no risk of misclassifying a genuinely new install
 * that happens to load before its own DataStore defaults settle.
 * [GarmentRepository.observeGarments] (with no status filter) is reused
 * rather than a dedicated `EXISTS`-style query — adding a new repository
 * method would have pushed [GarmentRepository]/`GarmentRepositoryImpl` past
 * detekt's `TooManyFunctions` threshold, and this device's real wardrobe
 * size makes the difference immaterial for a check that only ever runs
 * once at app startup. See `docs/adr/ADR-015-m16-onboarding.md`.
 */
class OnboardingRepositoryImpl
    @Inject
    constructor(
        private val onboardingDataStore: OnboardingDataStore,
        private val personalizationDataStore: PersonalizationDataStore,
        private val garmentRepository: GarmentRepository,
    ) : OnboardingRepository {
        override fun observeIsComplete(): Flow<Boolean> =
            combine(
                onboardingDataStore.observeCompleted(),
                personalizationDataStore.observe(),
                garmentRepository.observeGarments(GarmentFilter(status = null)).map { it.isNotEmpty() },
            ) { explicitlyCompleted, personalization, hasAnyGarment ->
                explicitlyCompleted || personalization.displayName != null || hasAnyGarment
            }

        override suspend fun markComplete() = onboardingDataStore.setCompleted()
    }
