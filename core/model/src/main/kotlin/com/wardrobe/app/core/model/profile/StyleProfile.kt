package com.wardrobe.app.core.model.profile

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.Money

enum class GenderPreference { MENSWEAR, WOMENSWEAR, BOTH }

/**
 * Scalar fields (occupation, gender preference, blurb, budget) live in DataStore
 * (`core:datastore`) — single-user, single-row, non-relational, no query/filter/join
 * requirement. [preferredBrandIds]/[avoidedCategoryIds] are Room junction tables
 * (`style_profile_preferred_brands`/`_avoided_categories`) instead, since both
 * reference other tables — see phase-3-persistence.md. This domain model composes
 * both sources; the repository implementation (Phase 5a) is what actually reads from
 * two different stores to build it.
 */
data class StyleProfile(
    val occupation: String?,
    val genderPreference: GenderPreference?,
    val preferenceBlurb: String?,
    val budgetMin: Money?,
    val budgetMax: Money?,
    val preferredBrandIds: Set<BrandId>,
    val avoidedCategoryIds: Set<CategoryId>,
)
