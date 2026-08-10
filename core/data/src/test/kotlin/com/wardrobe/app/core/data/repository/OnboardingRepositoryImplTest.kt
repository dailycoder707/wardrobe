package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.OnboardingDataStore
import com.wardrobe.app.core.datastore.preferences.PersonalizationDataStore
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRepositoryImplTest {
    private fun repository(
        explicitlyCompleted: Boolean = false,
        displayName: String? = null,
        hasAnyGarment: Boolean = false,
    ): OnboardingRepositoryImpl {
        val onboardingDataStore =
            mockk<OnboardingDataStore> {
                every { observeCompleted() } returns flowOf(explicitlyCompleted)
                coEvery { setCompleted() } returns Unit
            }
        val personalizationDataStore =
            mockk<PersonalizationDataStore> {
                every { observe() } returns flowOf(PersonalizationSettings.DEFAULT.copy(displayName = displayName))
            }
        val garments = if (hasAnyGarment) listOf(mockk<Garment>()) else emptyList()
        val garmentRepository =
            mockk<GarmentRepository> {
                every { observeGarments(any()) } returns flowOf(garments)
            }
        return OnboardingRepositoryImpl(onboardingDataStore, personalizationDataStore, garmentRepository)
    }

    @Test
    fun `a brand-new install with no explicit flag, no name and no garments is not complete`() =
        runTest {
            val isComplete = repository().observeIsComplete().first()

            assertFalse(isComplete)
        }

    @Test
    fun `the explicit flag alone marks onboarding complete`() =
        runTest {
            val isComplete = repository(explicitlyCompleted = true).observeIsComplete().first()

            assertTrue(isComplete)
        }

    @Test
    fun `an existing saved display name marks onboarding complete even without the explicit flag`() =
        runTest {
            val isComplete = repository(displayName = "Palak").observeIsComplete().first()

            assertTrue(isComplete)
        }

    @Test
    fun `an existing garment marks onboarding complete even without the explicit flag or a name`() =
        runTest {
            val isComplete = repository(hasAnyGarment = true).observeIsComplete().first()

            assertTrue(isComplete)
        }

    @Test
    fun `markComplete writes the explicit flag through to the data store`() =
        runTest {
            val onboardingDataStore =
                mockk<OnboardingDataStore> {
                    every { observeCompleted() } returns flowOf(false)
                    coEvery { setCompleted() } returns Unit
                }
            val personalizationDataStore =
                mockk<PersonalizationDataStore> { every { observe() } returns flowOf(PersonalizationSettings.DEFAULT) }
            val garmentRepository =
                mockk<GarmentRepository> { every { observeGarments(any()) } returns flowOf(emptyList()) }
            val repository = OnboardingRepositoryImpl(onboardingDataStore, personalizationDataStore, garmentRepository)

            repository.markComplete()

            coVerify(exactly = 1) { onboardingDataStore.setCompleted() }
        }
}
