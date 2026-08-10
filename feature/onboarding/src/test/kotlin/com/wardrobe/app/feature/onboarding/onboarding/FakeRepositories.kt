package com.wardrobe.app.feature.onboarding.onboarding

import com.wardrobe.app.core.domain.repository.OnboardingRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeOnboardingRepository(
    initiallyComplete: Boolean = false,
) : OnboardingRepository {
    private val flow = MutableStateFlow(initiallyComplete)
    var markCompleteCallCount = 0
        private set

    override fun observeIsComplete(): Flow<Boolean> = flow.asStateFlow()

    override suspend fun markComplete() {
        markCompleteCallCount++
        flow.value = true
    }
}

class FakePersonalizationRepository(
    initial: PersonalizationSettings = PersonalizationSettings.DEFAULT,
) : PersonalizationRepository {
    val flow = MutableStateFlow(initial)

    override fun observe(): Flow<PersonalizationSettings> = flow.asStateFlow()

    override suspend fun setDisplayName(name: String?) {
        flow.value = flow.value.copy(displayName = name)
    }

    override suspend fun setGreetingStyle(style: GreetingStyle) {
        flow.value = flow.value.copy(greetingStyle = style)
    }

    override suspend fun setCustomHomeTitle(title: String?) {
        flow.value = flow.value.copy(customHomeTitle = title)
    }

    override suspend fun setAvatarImageUri(uri: String?) {
        flow.value = flow.value.copy(avatarImageUri = uri)
    }

    override suspend fun setShowGreeting(show: Boolean) {
        flow.value = flow.value.copy(showGreeting = show)
    }

    override suspend fun setShowWeatherCard(show: Boolean) {
        flow.value = flow.value.copy(showWeatherCard = show)
    }

    override suspend fun setShowRecommendationCard(show: Boolean) {
        flow.value = flow.value.copy(showRecommendationCard = show)
    }

    override suspend fun setShowWardrobeHealthCard(show: Boolean) {
        flow.value = flow.value.copy(showWardrobeHealthCard = show)
    }

    override suspend fun setShowInspirationCard(show: Boolean) {
        flow.value = flow.value.copy(showInspirationCard = show)
    }
}

class FakeStylistPreferencesRepository(
    initial: RecommendationPreferences = RecommendationPreferences(),
) : StylistPreferencesRepository {
    val flow = MutableStateFlow(initial)

    override fun observePreferences(): Flow<RecommendationPreferences> = flow.asStateFlow()

    override suspend fun setPreferences(preferences: RecommendationPreferences) {
        flow.value = preferences
    }
}
