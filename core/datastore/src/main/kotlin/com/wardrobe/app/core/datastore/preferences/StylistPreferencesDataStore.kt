package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Same unit-separator convention `ClosetPreferencesDataStore` uses for its
// recent-searches list — reused here for the preferred-dress-codes set.
private const val DRESS_CODE_SEPARATOR = "\u001F"

@Singleton
class StylistPreferencesDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observePreferences(): Flow<RecommendationPreferences> =
            dataStore.data.map { prefs ->
                val defaults = RecommendationPreferences()
                RecommendationPreferences(
                    includeShoes = prefs[PreferenceKeys.STYLIST_INCLUDE_SHOES] ?: defaults.includeShoes,
                    includeBags = prefs[PreferenceKeys.STYLIST_INCLUDE_BAGS] ?: defaults.includeBags,
                    includeJewelry = prefs[PreferenceKeys.STYLIST_INCLUDE_JEWELRY] ?: defaults.includeJewelry,
                    includeWatch = prefs[PreferenceKeys.STYLIST_INCLUDE_WATCH] ?: defaults.includeWatch,
                    includeBelt = prefs[PreferenceKeys.STYLIST_INCLUDE_BELT] ?: defaults.includeBelt,
                    includeHairAccessories =
                        prefs[PreferenceKeys.STYLIST_INCLUDE_HAIR_ACCESSORIES] ?: defaults.includeHairAccessories,
                    includeSunglasses = prefs[PreferenceKeys.STYLIST_INCLUDE_SUNGLASSES] ?: defaults.includeSunglasses,
                    preferFavorites = prefs[PreferenceKeys.STYLIST_PREFER_FAVORITES] ?: defaults.preferFavorites,
                    preferRarelyWorn = prefs[PreferenceKeys.STYLIST_PREFER_RARELY_WORN] ?: defaults.preferRarelyWorn,
                    avoidRecentlyWorn =
                        prefs[PreferenceKeys.STYLIST_AVOID_RECENTLY_WORN] ?: defaults.avoidRecentlyWorn,
                    maxRepeatIntervalDays =
                        prefs[PreferenceKeys.STYLIST_MAX_REPEAT_INTERVAL_DAYS] ?: defaults.maxRepeatIntervalDays,
                    favoriteColorBias = prefs[PreferenceKeys.STYLIST_FAVORITE_COLOR_BIAS] ?: defaults.favoriteColorBias,
                    preferredDressCodes = decodeDressCodes(prefs[PreferenceKeys.STYLIST_PREFERRED_DRESS_CODES]),
                    preferComfortableFit =
                        prefs[PreferenceKeys.STYLIST_PREFER_COMFORTABLE_FIT] ?: defaults.preferComfortableFit,
                )
            }

        suspend fun setPreferences(preferences: RecommendationPreferences) {
            dataStore.edit { prefs ->
                writePreferences(prefs, preferences)
                prefs[PreferenceKeys.STYLIST_PREFERENCES_UPDATED_AT] = System.currentTimeMillis()
            }
        }

        /** One-shot read of when this blob was last changed — see
         * `PersonalizationDataStore.lastModifiedAt`'s KDoc for why. */
        suspend fun lastModifiedAt(): Long =
            dataStore.data.map { it[PreferenceKeys.STYLIST_PREFERENCES_UPDATED_AT] ?: 0L }.first()

        /** Phase 8 sync apply path only — see
         * `PersonalizationDataStore.applyFromSync`'s KDoc for why this is
         * separate from [setPreferences]. */
        suspend fun applyFromSync(
            preferences: RecommendationPreferences,
            modifiedAt: Long,
        ) {
            dataStore.edit { prefs ->
                writePreferences(prefs, preferences)
                prefs[PreferenceKeys.STYLIST_PREFERENCES_UPDATED_AT] = modifiedAt
            }
        }

        private fun writePreferences(
            prefs: MutablePreferences,
            preferences: RecommendationPreferences,
        ) {
            prefs[PreferenceKeys.STYLIST_INCLUDE_SHOES] = preferences.includeShoes
            prefs[PreferenceKeys.STYLIST_INCLUDE_BAGS] = preferences.includeBags
            prefs[PreferenceKeys.STYLIST_INCLUDE_JEWELRY] = preferences.includeJewelry
            prefs[PreferenceKeys.STYLIST_INCLUDE_WATCH] = preferences.includeWatch
            prefs[PreferenceKeys.STYLIST_INCLUDE_BELT] = preferences.includeBelt
            prefs[PreferenceKeys.STYLIST_INCLUDE_HAIR_ACCESSORIES] = preferences.includeHairAccessories
            prefs[PreferenceKeys.STYLIST_INCLUDE_SUNGLASSES] = preferences.includeSunglasses
            prefs[PreferenceKeys.STYLIST_PREFER_FAVORITES] = preferences.preferFavorites
            prefs[PreferenceKeys.STYLIST_PREFER_RARELY_WORN] = preferences.preferRarelyWorn
            prefs[PreferenceKeys.STYLIST_AVOID_RECENTLY_WORN] = preferences.avoidRecentlyWorn
            prefs[PreferenceKeys.STYLIST_MAX_REPEAT_INTERVAL_DAYS] = preferences.maxRepeatIntervalDays
            prefs[PreferenceKeys.STYLIST_FAVORITE_COLOR_BIAS] = preferences.favoriteColorBias
            prefs[PreferenceKeys.STYLIST_PREFERRED_DRESS_CODES] =
                preferences.preferredDressCodes.joinToString(DRESS_CODE_SEPARATOR) { it.name }
            prefs[PreferenceKeys.STYLIST_PREFER_COMFORTABLE_FIT] = preferences.preferComfortableFit
        }

        private fun decodeDressCodes(raw: String?): Set<DressCode> =
            raw
                ?.split(DRESS_CODE_SEPARATOR)
                ?.mapNotNull { runCatching { DressCode.valueOf(it) }.getOrNull() }
                ?.toSet()
                .orEmpty()
    }
