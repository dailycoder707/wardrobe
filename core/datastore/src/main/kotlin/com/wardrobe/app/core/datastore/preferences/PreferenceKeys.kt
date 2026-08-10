package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wardrobe.app.core.model.ai.AiCapability

/**
 * Keys for the single `Preferences` file this app uses. Kept in one object rather
 * than scattered per class so a key is never accidentally declared twice with
 * different types — see phase-3-persistence.md's note on why StyleProfile's scalar
 * fields live here rather than in Room.
 */
internal object PreferenceKeys {
    // StyleProfile scalars
    val OCCUPATION = stringPreferencesKey("style_profile_occupation")
    val GENDER_PREFERENCE = stringPreferencesKey("style_profile_gender_preference")
    val PREFERENCE_BLURB = stringPreferencesKey("style_profile_preference_blurb")
    val BUDGET_MIN_AMOUNT = doublePreferencesKey("style_profile_budget_min_amount")
    val BUDGET_MIN_CURRENCY = stringPreferencesKey("style_profile_budget_min_currency")
    val BUDGET_MAX_AMOUNT = doublePreferencesKey("style_profile_budget_max_amount")
    val BUDGET_MAX_CURRENCY = stringPreferencesKey("style_profile_budget_max_currency")

    // Deliberately not here yet: theme/units/grid-density/last-backup-time. Phase 3
    // didn't define a domain repository interface for general UserPreferences (only
    // for StyleProfile), and this phase implements existing interfaces rather than
    // inventing new ones — add these keys alongside whatever Phase 5f's Settings
    // work actually needs, not speculatively now.

    // PersonalizationSettings
    val DISPLAY_NAME = stringPreferencesKey("personalization_display_name")
    val GREETING_STYLE = stringPreferencesKey("personalization_greeting_style")
    val CUSTOM_HOME_TITLE = stringPreferencesKey("personalization_custom_home_title")
    val AVATAR_IMAGE_URI = stringPreferencesKey("personalization_avatar_image_uri")
    val SHOW_GREETING = booleanPreferencesKey("personalization_show_greeting")
    val SHOW_WEATHER_CARD = booleanPreferencesKey("personalization_show_weather_card")
    val SHOW_RECOMMENDATION_CARD = booleanPreferencesKey("personalization_show_recommendation_card")
    val SHOW_WARDROBE_HEALTH_CARD = booleanPreferencesKey("personalization_show_wardrobe_health_card")
    val SHOW_INSPIRATION_CARD = booleanPreferencesKey("personalization_show_inspiration_card")

    // Closet browsing preferences (Phase 5c) — "persist user preference" for sort
    // order and grid density (`component-library.md`'s Grid Density Control).
    val CLOSET_SORT_FIELD = stringPreferencesKey("closet_sort_field")
    val CLOSET_SORT_DIRECTION = stringPreferencesKey("closet_sort_direction")
    val CLOSET_GRID_COLUMN_COUNT = intPreferencesKey("closet_grid_column_count")
    val CLOSET_RECENT_SEARCHES = stringPreferencesKey("closet_recent_searches")

    // Stylist Preferences (Phase 6) — the styling engine's recommendation rules.
    val STYLIST_INCLUDE_SHOES = booleanPreferencesKey("stylist_include_shoes")
    val STYLIST_INCLUDE_BAGS = booleanPreferencesKey("stylist_include_bags")
    val STYLIST_INCLUDE_JEWELRY = booleanPreferencesKey("stylist_include_jewelry")
    val STYLIST_INCLUDE_WATCH = booleanPreferencesKey("stylist_include_watch")
    val STYLIST_INCLUDE_BELT = booleanPreferencesKey("stylist_include_belt")
    val STYLIST_INCLUDE_HAIR_ACCESSORIES = booleanPreferencesKey("stylist_include_hair_accessories")
    val STYLIST_INCLUDE_SUNGLASSES = booleanPreferencesKey("stylist_include_sunglasses")
    val STYLIST_PREFER_FAVORITES = booleanPreferencesKey("stylist_prefer_favorites")
    val STYLIST_PREFER_RARELY_WORN = booleanPreferencesKey("stylist_prefer_rarely_worn")
    val STYLIST_AVOID_RECENTLY_WORN = booleanPreferencesKey("stylist_avoid_recently_worn")
    val STYLIST_MAX_REPEAT_INTERVAL_DAYS = intPreferencesKey("stylist_max_repeat_interval_days")
    val STYLIST_FAVORITE_COLOR_BIAS = booleanPreferencesKey("stylist_favorite_color_bias")
    val STYLIST_PREFERRED_DRESS_CODES = stringPreferencesKey("stylist_preferred_dress_codes")
    val STYLIST_PREFER_COMFORTABLE_FIT = booleanPreferencesKey("stylist_prefer_comfortable_fit")

    // Weather Settings (Phase 7) — see phase-7-context-aware-assistant.md.
    val WEATHER_USE_WEATHER = booleanPreferencesKey("weather_use_weather")
    val WEATHER_OFFLINE_ONLY = booleanPreferencesKey("weather_offline_only")
    val WEATHER_USE_DEVICE_LOCATION = booleanPreferencesKey("weather_use_device_location")
    val WEATHER_MANUAL_LATITUDE = doublePreferencesKey("weather_manual_latitude")
    val WEATHER_MANUAL_LONGITUDE = doublePreferencesKey("weather_manual_longitude")
    val WEATHER_MANUAL_LOCATION_LABEL = stringPreferencesKey("weather_manual_location_label")
    val WEATHER_TEMPERATURE_UNIT = stringPreferencesKey("weather_temperature_unit")
    val WEATHER_REFRESH_INTERVAL_HOURS = intPreferencesKey("weather_refresh_interval_hours")

    // Phase 8 — last-modified timestamps for the two preference blobs synced
    // as whole-object last-writer-wins (see phase-8-multi-device-sync.md's
    // "Preferences" section). StyleProfile/ClosetPreferences/WeatherPreferences
    // are deliberately NOT synced this way — see that section for why.
    val PERSONALIZATION_UPDATED_AT = longPreferencesKey("personalization_updated_at")
    val STYLIST_PREFERENCES_UPDATED_AT = longPreferencesKey("stylist_preferences_updated_at")

    // Wardrobe Sync settings (Phase 8).
    val SYNC_AUTO_ENABLED = booleanPreferencesKey("sync_auto_enabled")
    val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
    val SYNC_CHARGING_ONLY = booleanPreferencesKey("sync_charging_only")

    // Onboarding (M16) — only ever explicitly written `true` on completion or
    // skip; absence means "not explicitly completed," which
    // `OnboardingRepositoryImpl` combines with real upgrade signals (an
    // existing display name, an existing garment) rather than trusting this
    // one flag alone. See docs/adr/ADR-015-m16-onboarding.md.
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    // AI Provider preferences (Add-to-Wardrobe v2, ADR-012) — one key set per
    // AiCapability rather than 5 capabilities x 7 fields of hand-declared
    // constants; a function still guarantees one canonical, type-safe key
    // per (capability, field) pair, the same collision-avoidance this
    // object's own doc comment asks for. The API key itself is never a
    // DataStore key at all — see `core:ai`'s `EncryptedApiKeyStore`.
    fun aiProviderMode(capability: AiCapability): Preferences.Key<String> =
        stringPreferencesKey("ai_${capability.name}_mode")

    fun aiProviderVendor(capability: AiCapability): Preferences.Key<String> =
        stringPreferencesKey("ai_${capability.name}_vendor")

    fun aiProviderBaseUrl(capability: AiCapability): Preferences.Key<String> =
        stringPreferencesKey("ai_${capability.name}_base_url")

    fun aiProviderModel(capability: AiCapability): Preferences.Key<String> =
        stringPreferencesKey("ai_${capability.name}_model")

    fun aiProviderCostRate(capability: AiCapability): Preferences.Key<Double> =
        doublePreferencesKey("ai_${capability.name}_cost_rate")

    fun aiProviderConsentGrantedAt(capability: AiCapability): Preferences.Key<Long> =
        longPreferencesKey("ai_${capability.name}_consent_granted_at")

    fun aiProviderConsentHost(capability: AiCapability): Preferences.Key<String> =
        stringPreferencesKey("ai_${capability.name}_consent_host")
}
