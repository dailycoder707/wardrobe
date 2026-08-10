package com.wardrobe.app.feature.settings.navigation

import kotlinx.serialization.Serializable

/** M15 Part 4 — the Profile/Settings hub, reached from Home's header. Links
 * out to [AiProvidersRoute]/[WardrobeSyncRoute] and `feature:outfits`'
 * `StylistPreferencesRoute` rather than duplicating any of their state. */
@Serializable
object ProfileRoute
