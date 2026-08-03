package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.PersonalizationDataStore
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** A thin pass-through — [PersonalizationDataStore] already does all the real work;
 * this class exists only so `feature:*` modules depend on the `core:domain`
 * interface, never on `core:datastore` directly (Phase 1 Section 1's dependency
 * rule). */
class PersonalizationRepositoryImpl
    @Inject
    constructor(
        private val dataStore: PersonalizationDataStore,
    ) : PersonalizationRepository {
        override fun observe(): Flow<PersonalizationSettings> = dataStore.observe()

        override suspend fun setDisplayName(name: String?) = dataStore.setDisplayName(name)

        override suspend fun setGreetingStyle(style: GreetingStyle) = dataStore.setGreetingStyle(style)

        override suspend fun setCustomHomeTitle(title: String?) = dataStore.setCustomHomeTitle(title)

        override suspend fun setAvatarImageUri(uri: String?) = dataStore.setAvatarImageUri(uri)

        override suspend fun setShowGreeting(show: Boolean) = dataStore.setShowGreeting(show)

        override suspend fun setShowWeatherCard(show: Boolean) = dataStore.setShowWeatherCard(show)

        override suspend fun setShowRecommendationCard(show: Boolean) = dataStore.setShowRecommendationCard(show)

        override suspend fun setShowWardrobeHealthCard(show: Boolean) = dataStore.setShowWardrobeHealthCard(show)

        override suspend fun setShowInspirationCard(show: Boolean) = dataStore.setShowInspirationCard(show)
    }
