package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.StyleRule
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import java.time.LocalDate

/**
 * One immutable snapshot of everything [com.wardrobe.app.core.data.repository.StylingEngineRepositoryImpl]
 * needs, loaded once per `suggestOutfits`/`suggestForItem` call — every source
 * repository is `Flow`-based, but the engine itself works on a plain snapshot
 * (`.first()`) rather than staying subscribed, since a suggestion is generated
 * on demand ("Generate Another Look"), not continuously recomputed.
 *
 * [weather] and [plannedOccasionDressCode] are Phase 7's context-refinement
 * additions — both are best-effort and `null`-safe throughout the scoring
 * pipeline (Constitution rule 12, Context Refinement Rule): a call with
 * neither populated still produces complete, scored outfits exactly like
 * Phase 6 did.
 */
internal data class EngineInput(
    val candidateGarments: List<Garment>,
    val garmentsById: Map<GarmentId, Garment>,
    val categoriesById: Map<CategoryId, Category>,
    val colorsById: Map<ColorId, Color>,
    val activeRules: List<StyleRule>,
    val preferences: RecommendationPreferences,
    val costPerWearByGarmentId: Map<GarmentId, CostPerWearEntry>,
    val favoriteColorIds: List<ColorId>,
    val today: LocalDate,
    val weather: WeatherSnapshot?,
    val plannedOccasionDressCode: DressCode? = null,
)
