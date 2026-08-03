package com.wardrobe.app.core.model.profile

import java.time.LocalTime

private const val MORNING_STARTS_AT_HOUR = 5
private const val AFTERNOON_STARTS_AT_HOUR = 12
private const val EVENING_STARTS_AT_HOUR = 18

/**
 * How the greeting addresses [PersonalizationSettings.displayName]. `TimeOfDay` is
 * the only style that varies by clock time; the other two are fixed phrasings some
 * users may prefer instead. There is no fourth "evening" style to pick independently
 * — morning/afternoon/evening are one style's three time-based variants, not three
 * separate styles, matching how the request's own examples group them.
 */
enum class GreetingStyle { TIME_OF_DAY, WELCOME_BACK, READY_FOR_TODAY }

/**
 * Every field the Home screen (Phase 5c) needs to personalize itself, backed by
 * DataStore (`core:datastore`), never hardcoded anywhere else in the app. A blank or
 * null [displayName] is a real, expected state (day one, before the user has set
 * one) — [greetingText] handles it explicitly rather than producing "Good Morning,"
 * with a dangling comma.
 */
data class PersonalizationSettings(
    val displayName: String?,
    val greetingStyle: GreetingStyle,
    val customHomeTitle: String?,
    val avatarImageUri: String?,
    val showGreeting: Boolean,
    val showWeatherCard: Boolean,
    val showRecommendationCard: Boolean,
    val showWardrobeHealthCard: Boolean,
    val showInspirationCard: Boolean,
) {
    companion object {
        val DEFAULT =
            PersonalizationSettings(
                displayName = null,
                greetingStyle = GreetingStyle.TIME_OF_DAY,
                customHomeTitle = null,
                avatarImageUri = null,
                showGreeting = true,
                showWeatherCard = true,
                showRecommendationCard = true,
                showWardrobeHealthCard = true,
                showInspirationCard = true,
            )
    }
}

/**
 * The one place greeting text is assembled — every screen renders this, never its
 * own hardcoded string, so there is exactly one function to check when verifying no
 * name is ever baked in literally. Pure and clock-testable: callers pass `now`
 * rather than this function reading the system clock itself.
 */
fun PersonalizationSettings.greetingText(now: LocalTime): String {
    val name = displayName?.trim().takeUnless { it.isNullOrEmpty() }
    return when (greetingStyle) {
        GreetingStyle.TIME_OF_DAY -> {
            val timeOfDay =
                when {
                    now.hour < MORNING_STARTS_AT_HOUR -> "Evening"
                    now.hour < AFTERNOON_STARTS_AT_HOUR -> "Morning"
                    now.hour < EVENING_STARTS_AT_HOUR -> "Afternoon"
                    else -> "Evening"
                }
            if (name != null) "Good $timeOfDay, $name" else "Good $timeOfDay"
        }

        GreetingStyle.WELCOME_BACK -> {
            if (name != null) "Welcome back, $name" else "Welcome back"
        }

        GreetingStyle.READY_FOR_TODAY -> {
            if (name != null) "Ready for today, $name" else "Ready for today"
        }
    }
}
