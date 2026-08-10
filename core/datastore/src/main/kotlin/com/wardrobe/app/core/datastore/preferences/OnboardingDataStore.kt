package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M16 — the raw, explicit "has the user finished (or explicitly skipped)
 * first-run onboarding" flag. Deliberately dumb, mirroring every other
 * `*DataStore` class in this file's shared `Preferences` file: it only ever
 * reads/writes [PreferenceKeys.ONBOARDING_COMPLETED] as-is, absent meaning
 * `false`. It has no opinion about existing-user upgrades — see
 * `OnboardingRepositoryImpl` (`core:data`) for that logic, which combines
 * this flag with other real signals rather than this class fabricating one.
 */
@Singleton
class OnboardingDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observeCompleted(): Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[PreferenceKeys.ONBOARDING_COMPLETED] == true }

        suspend fun setCompleted() {
            dataStore.edit { prefs -> prefs[PreferenceKeys.ONBOARDING_COMPLETED] = true }
        }
    }
