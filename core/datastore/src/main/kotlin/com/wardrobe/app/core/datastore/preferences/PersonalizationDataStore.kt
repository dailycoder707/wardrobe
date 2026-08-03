package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place [PersonalizationSettings] is read from or written to disk —
 * `core:domain`'s `PersonalizationRepository` (implemented in `core:data`) is a thin
 * pass-through over this class. Every default matches
 * `PersonalizationSettings.DEFAULT` so a fresh install and an explicitly-reset
 * preference file behave identically.
 */
@Singleton
class PersonalizationDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observe(): Flow<PersonalizationSettings> =
            dataStore.data.map { prefs ->
                val default = PersonalizationSettings.DEFAULT
                PersonalizationSettings(
                    displayName = prefs[PreferenceKeys.DISPLAY_NAME],
                    greetingStyle =
                        prefs[PreferenceKeys.GREETING_STYLE]?.let {
                            runCatching { GreetingStyle.valueOf(it) }.getOrNull()
                        } ?: default.greetingStyle,
                    customHomeTitle = prefs[PreferenceKeys.CUSTOM_HOME_TITLE],
                    avatarImageUri = prefs[PreferenceKeys.AVATAR_IMAGE_URI],
                    showGreeting = prefs[PreferenceKeys.SHOW_GREETING] ?: default.showGreeting,
                    showWeatherCard = prefs[PreferenceKeys.SHOW_WEATHER_CARD] ?: default.showWeatherCard,
                    showRecommendationCard =
                        prefs[PreferenceKeys.SHOW_RECOMMENDATION_CARD]
                            ?: default.showRecommendationCard,
                    showWardrobeHealthCard =
                        prefs[PreferenceKeys.SHOW_WARDROBE_HEALTH_CARD]
                            ?: default.showWardrobeHealthCard,
                    showInspirationCard = prefs[PreferenceKeys.SHOW_INSPIRATION_CARD] ?: default.showInspirationCard,
                )
            }

        /** One-shot read of when this blob was last changed — Phase 8's
         * whole-object last-writer-wins sync compares this across devices
         * rather than tracking individual fields. */
        suspend fun lastModifiedAt(): Long =
            dataStore.data.map { it[PreferenceKeys.PERSONALIZATION_UPDATED_AT] ?: 0L }.first()

        /** Phase 8 sync apply path only — writes every field at once when the
         * remote copy of this blob is newer, distinct from the Settings UI's
         * one-field-at-a-time setters below. */
        suspend fun applyFromSync(
            settings: PersonalizationSettings,
            modifiedAt: Long,
        ) {
            dataStore.edit { prefs ->
                setOrRemove(prefs, PreferenceKeys.DISPLAY_NAME, settings.displayName?.trim())
                prefs[PreferenceKeys.GREETING_STYLE] = settings.greetingStyle.name
                setOrRemove(prefs, PreferenceKeys.CUSTOM_HOME_TITLE, settings.customHomeTitle?.trim())
                setOrRemove(prefs, PreferenceKeys.AVATAR_IMAGE_URI, settings.avatarImageUri)
                prefs[PreferenceKeys.SHOW_GREETING] = settings.showGreeting
                prefs[PreferenceKeys.SHOW_WEATHER_CARD] = settings.showWeatherCard
                prefs[PreferenceKeys.SHOW_RECOMMENDATION_CARD] = settings.showRecommendationCard
                prefs[PreferenceKeys.SHOW_WARDROBE_HEALTH_CARD] = settings.showWardrobeHealthCard
                prefs[PreferenceKeys.SHOW_INSPIRATION_CARD] = settings.showInspirationCard
                prefs[PreferenceKeys.PERSONALIZATION_UPDATED_AT] = modifiedAt
            }
        }

        suspend fun setDisplayName(name: String?) = edit(PreferenceKeys.DISPLAY_NAME, name?.trim())

        suspend fun setGreetingStyle(style: GreetingStyle) = edit(PreferenceKeys.GREETING_STYLE, style.name)

        suspend fun setCustomHomeTitle(title: String?) = edit(PreferenceKeys.CUSTOM_HOME_TITLE, title?.trim())

        suspend fun setAvatarImageUri(uri: String?) = edit(PreferenceKeys.AVATAR_IMAGE_URI, uri)

        suspend fun setShowGreeting(show: Boolean) = edit(PreferenceKeys.SHOW_GREETING, show)

        suspend fun setShowWeatherCard(show: Boolean) = edit(PreferenceKeys.SHOW_WEATHER_CARD, show)

        suspend fun setShowRecommendationCard(show: Boolean) = edit(PreferenceKeys.SHOW_RECOMMENDATION_CARD, show)

        suspend fun setShowWardrobeHealthCard(show: Boolean) = edit(PreferenceKeys.SHOW_WARDROBE_HEALTH_CARD, show)

        suspend fun setShowInspirationCard(show: Boolean) = edit(PreferenceKeys.SHOW_INSPIRATION_CARD, show)

        private suspend fun <T : Any> edit(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            dataStore.edit { prefs ->
                setOrRemove(prefs, key, value)
                prefs[PreferenceKeys.PERSONALIZATION_UPDATED_AT] = System.currentTimeMillis()
            }
        }
    }
