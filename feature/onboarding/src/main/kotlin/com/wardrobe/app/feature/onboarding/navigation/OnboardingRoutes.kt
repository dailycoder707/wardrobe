package com.wardrobe.app.feature.onboarding.navigation

import kotlinx.serialization.Serializable

/** M16 — first-run onboarding. Five flat top-level routes in the app's one
 * existing `NavHost` (no nested graph, no second navigation stack) — each
 * screen persists directly through its own already-existing repository as
 * the user goes, so there is no cross-screen draft state that would need a
 * shared, graph-scoped ViewModel. See `docs/adr/ADR-015-m16-onboarding.md`. */
@Serializable
object OnboardingWelcomeRoute

@Serializable
object OnboardingNameRoute

@Serializable
object OnboardingStyleRoute

@Serializable
object OnboardingAiRoute

@Serializable
object OnboardingFinishRoute
