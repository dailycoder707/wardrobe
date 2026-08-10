package com.wardrobe.app.feature.onboarding.onboarding

import app.cash.turbine.test
import com.wardrobe.app.core.domain.profile.MAX_DISPLAY_NAME_LENGTH
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingNameViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving a valid name persists it through PersonalizationRepository`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingNameViewModel(personalization)

            viewModel.uiState.test {
                assertEquals("", awaitItem().nameDraft)
                viewModel.onNameDraftChanged("Alex")
                assertEquals("Alex", awaitItem().nameDraft)
                viewModel.onSaveName()
                assertTrue(awaitItem().isSaving)
                val saved = awaitItem()
                assertTrue(saved.didSave)
                assertEquals(null, saved.nameError)
            }
            assertEquals("Alex", personalization.flow.value.displayName)
        }

    @Test
    fun `saving trims whitespace before persisting`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingNameViewModel(personalization)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onNameDraftChanged("  Alex  ")
                awaitItem()
                viewModel.onSaveName()
                awaitItem()
                awaitItem()
            }
            assertEquals("Alex", personalization.flow.value.displayName)
        }

    @Test
    fun `a blank name is rejected and PersonalizationRepository is never called`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingNameViewModel(personalization)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onNameDraftChanged("   ")
                awaitItem()
                viewModel.onSaveName()
                val afterSave = awaitItem()
                assertEquals("Name can't be empty.", afterSave.nameError)
                assertEquals(false, afterSave.didSave)
            }
            assertEquals(null, personalization.flow.value.displayName)
        }

    @Test
    fun `a name over the maximum length is rejected`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingNameViewModel(personalization)
            val tooLong = "A".repeat(MAX_DISPLAY_NAME_LENGTH + 1)

            viewModel.uiState.test {
                awaitItem()
                viewModel.onNameDraftChanged(tooLong)
                awaitItem()
                viewModel.onSaveName()
                val afterSave = awaitItem()
                assertTrue(afterSave.nameError!!.contains(MAX_DISPLAY_NAME_LENGTH.toString()))
            }
            assertEquals(null, personalization.flow.value.displayName)
        }

    @Test
    fun `a Unicode name is preserved exactly when saved`() =
        runTest {
            val personalization = FakePersonalizationRepository()
            val viewModel = OnboardingNameViewModel(personalization)
            val unicodeName = "Élodie 李雷"

            viewModel.uiState.test {
                awaitItem()
                viewModel.onNameDraftChanged(unicodeName)
                awaitItem()
                viewModel.onSaveName()
                awaitItem()
                val saved = awaitItem()
                assertTrue(saved.didSave)
            }
            assertEquals(unicodeName, personalization.flow.value.displayName)
        }

    @Test
    fun `never saving leaves the name genuinely unset, not defaulted`() =
        runTest {
            val personalization = FakePersonalizationRepository(PersonalizationSettings.DEFAULT)
            OnboardingNameViewModel(personalization)

            assertEquals(null, personalization.flow.value.displayName)
        }
}
