package com.wardrobe.app.feature.calendar.navigation

import kotlinx.serialization.Serializable

/** Calendar's single nav-dock destination. Day Detail is an in-screen panel
 * and Wear History is an in-screen list/calendar toggle over the same data
 * (`docs/design/screen-specifications.md` §10–11) — neither is a separate
 * route. */
@Serializable
object CalendarRoute
