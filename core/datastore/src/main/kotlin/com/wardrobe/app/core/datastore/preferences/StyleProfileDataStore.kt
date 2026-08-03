package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.profile.GenderPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Just the scalar fields of `StyleProfile` (core:model) — the relational parts
 * (preferred brands, avoided categories) live in Room, see phase-3-persistence.md.
 * `core:data`'s `StyleProfileRepositoryImpl` composes this with those. */
data class StyleProfileScalars(
    val occupation: String?,
    val genderPreference: GenderPreference?,
    val preferenceBlurb: String?,
    val budgetMin: Money?,
    val budgetMax: Money?,
)

@Singleton
class StyleProfileDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observe(): Flow<StyleProfileScalars> =
            dataStore.data.map { prefs ->
                StyleProfileScalars(
                    occupation = prefs[PreferenceKeys.OCCUPATION],
                    genderPreference =
                        prefs[PreferenceKeys.GENDER_PREFERENCE]?.let {
                            runCatching { GenderPreference.valueOf(it) }.getOrNull()
                        },
                    preferenceBlurb = prefs[PreferenceKeys.PREFERENCE_BLURB],
                    budgetMin = moneyFrom(prefs, PreferenceKeys.BUDGET_MIN_AMOUNT, PreferenceKeys.BUDGET_MIN_CURRENCY),
                    budgetMax = moneyFrom(prefs, PreferenceKeys.BUDGET_MAX_AMOUNT, PreferenceKeys.BUDGET_MAX_CURRENCY),
                )
            }

        suspend fun updateScalars(
            occupation: String?,
            genderPreference: GenderPreference?,
            preferenceBlurb: String?,
        ) {
            dataStore.edit { prefs ->
                setOrRemove(prefs, PreferenceKeys.OCCUPATION, occupation)
                setOrRemove(prefs, PreferenceKeys.GENDER_PREFERENCE, genderPreference?.name)
                setOrRemove(prefs, PreferenceKeys.PREFERENCE_BLURB, preferenceBlurb)
            }
        }

        suspend fun updateBudget(
            min: Money?,
            max: Money?,
        ) {
            dataStore.edit { prefs ->
                setOrRemove(prefs, PreferenceKeys.BUDGET_MIN_AMOUNT, min?.amount)
                setOrRemove(prefs, PreferenceKeys.BUDGET_MIN_CURRENCY, min?.currencyCode)
                setOrRemove(prefs, PreferenceKeys.BUDGET_MAX_AMOUNT, max?.amount)
                setOrRemove(prefs, PreferenceKeys.BUDGET_MAX_CURRENCY, max?.currencyCode)
            }
        }

        private fun moneyFrom(
            prefs: Preferences,
            amountKey: Preferences.Key<Double>,
            currencyKey: Preferences.Key<String>,
        ): Money? {
            val amount = prefs[amountKey]
            val currency = prefs[currencyKey]
            return if (amount != null && currency != null) Money(amount, currency) else null
        }
    }

/** Shared by every typed DataStore wrapper in this package — `null` removes the key
 * rather than writing a sentinel, so `observe()` can tell "unset" apart from any real
 * value without a magic-string convention. */
internal fun <T : Any> setOrRemove(
    prefs: androidx.datastore.preferences.core.MutablePreferences,
    key: Preferences.Key<T>,
    value: T?,
) {
    if (value == null) prefs.remove(key) else prefs[key] = value
}
