package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import kotlinx.coroutines.flow.Flow

/**
 * Backed entirely by DataStore (`core:datastore`) — every field here is exactly what
 * Home (Phase 5c) needs to never hardcode a name, a title, or which cards show.
 * [observe] is a `Flow` specifically so a live display-name edit reaches every
 * open screen immediately, with no restart — the same reactivity every other
 * DataStore-backed read in this app already relies on.
 */
interface PersonalizationRepository {
    fun observe(): Flow<PersonalizationSettings>

    suspend fun setDisplayName(name: String?)

    suspend fun setGreetingStyle(style: GreetingStyle)

    suspend fun setCustomHomeTitle(title: String?)

    suspend fun setAvatarImageUri(uri: String?)

    suspend fun setShowGreeting(show: Boolean)

    suspend fun setShowWeatherCard(show: Boolean)

    suspend fun setShowRecommendationCard(show: Boolean)

    suspend fun setShowWardrobeHealthCard(show: Boolean)

    suspend fun setShowInspirationCard(show: Boolean)
}
