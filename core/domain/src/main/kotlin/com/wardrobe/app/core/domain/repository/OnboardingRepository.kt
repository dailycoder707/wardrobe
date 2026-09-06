package com.wardrobe.app.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * M16 — whether first-run Onboarding should show. [observeIsComplete] is
 * never based solely on one stored flag; the real implementation also
 * treats a device with genuine pre-existing usage (an existing display
 * name, an existing garment) as already onboarded, so an existing install
 * upgrading to a version that has onboarding at all is never forced to
 * repeat it (see `docs/adr/ADR-015-m16-onboarding.md`). [markComplete] is
 * called once the flow finishes — including when every optional step was
 * skipped, since skipping the flow is a legitimate way to finish it, just
 * never a route to fabricated data.
 */
interface OnboardingRepository {
    fun observeIsComplete(): Flow<Boolean>

    suspend fun markComplete()
}
