package com.wardrobe.app.feature.outfits.fakes

import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.intelligence.CapsuleSuggestion
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.model.intelligence.DailyBrief
import com.wardrobe.app.core.model.intelligence.DuplicateGroup
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.ShoppingGapSuggestion
import com.wardrobe.app.core.model.intelligence.WardrobeAlerts
import com.wardrobe.app.core.model.intelligence.WardrobeHealthScore
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.Feedback
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.StyleRule
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.wear.WearEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeGarmentRepository(
    initial: List<Garment> = emptyList(),
) : GarmentRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> =
        flow.map { garments -> garments.filter { filter.status == null || it.status == filter.status } }

    override fun observeGarment(id: com.wardrobe.app.core.model.common.GarmentId): Flow<Garment?> =
        flow.map { garments -> garments.firstOrNull { it.id == id } }

    override suspend fun getGarment(id: com.wardrobe.app.core.model.common.GarmentId): Garment? =
        flow.value.firstOrNull { it.id == id }

    override suspend fun saveGarment(garment: Garment): com.wardrobe.app.core.model.common.GarmentId {
        flow.value = flow.value + garment
        return garment.id
    }

    override suspend fun updateGarment(garment: Garment) {
        flow.value = flow.value.map { if (it.id == garment.id) garment else it }
    }

    override suspend fun setStatus(
        id: com.wardrobe.app.core.model.common.GarmentId,
        status: com.wardrobe.app.core.model.garment.GarmentStatus,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun setFavorite(
        id: com.wardrobe.app.core.model.common.GarmentId,
        isFavorite: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setInLaundry(
        id: com.wardrobe.app.core.model.common.GarmentId,
        isInLaundry: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isInLaundry = isInLaundry) else it }
    }

    override suspend fun deleteGarment(id: com.wardrobe.app.core.model.common.GarmentId) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun findPotentialDuplicates(
        categoryId: CategoryId,
        colorId: com.wardrobe.app.core.model.common.ColorId?,
        excludeGarmentId: com.wardrobe.app.core.model.common.GarmentId?,
    ): List<Garment> =
        flow.value.filter { garment ->
            garment.categoryId == categoryId &&
                garment.status == com.wardrobe.app.core.model.garment.GarmentStatus.ACTIVE &&
                (excludeGarmentId == null || garment.id != excludeGarmentId) &&
                (colorId == null || garment.primaryColorId == colorId)
        }
}

/** [garmentsWithWearHistory] mirrors the real repository's RESTRICT FK — deleting
 * one of these ids throws, matching `OutfitRepositoryImpl.deleteOutfit`'s
 * documented contract so tests can exercise the delete-blocked path. */
class FakeOutfitRepository(
    initial: List<Outfit> = emptyList(),
    private val outfitsWithWearHistory: Set<Long> = emptySet(),
) : OutfitRepository {
    private val flow = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1

    override fun observeOutfits(filter: OutfitFilter): Flow<List<Outfit>> =
        flow.map { outfits ->
            val searchQuery = filter.searchQuery
            outfits.filter { outfit ->
                (filter.isSaved == null || outfit.isSaved == filter.isSaved) &&
                    (filter.isArchived == null || outfit.isArchived == filter.isArchived) &&
                    (filter.isFavorite == null || outfit.isFavorite == filter.isFavorite) &&
                    (filter.occasionId == null || outfit.occasionId == filter.occasionId) &&
                    (
                        searchQuery == null ||
                            outfit.name
                                .orEmpty()
                                .lowercase()
                                .contains(searchQuery.lowercase())
                    )
            }
        }

    override fun observeOutfit(id: OutfitId): Flow<Outfit?> =
        flow.map { outfits -> outfits.firstOrNull { it.id == id } }

    override suspend fun getOutfit(id: OutfitId): Outfit? = flow.value.firstOrNull { it.id == id }

    override suspend fun saveOutfit(outfit: Outfit): OutfitId {
        if (outfit.id.value == 0L) {
            val newOutfit = outfit.copy(id = OutfitId(nextId++))
            flow.value = flow.value + newOutfit
            return newOutfit.id
        }
        flow.value = flow.value.map { if (it.id == outfit.id) outfit else it }
        return outfit.id
    }

    override suspend fun setFavorite(
        id: OutfitId,
        isFavorite: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setArchived(
        id: OutfitId,
        isArchived: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isArchived = isArchived) else it }
    }

    override suspend fun deleteOutfit(id: OutfitId) {
        if (id.value in outfitsWithWearHistory) {
            throw IllegalStateException("Outfit $id has wear history and cannot be deleted")
        }
        flow.value = flow.value.filterNot { it.id == id }
    }
}

class FakeWearEventRepository(
    initial: List<WearEvent> = emptyList(),
) : WearEventRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeEvents(range: DateRange): Flow<List<WearEvent>> =
        flow.map { events -> events.filter { !it.date.isBefore(range.start) && !it.date.isAfter(range.end) } }

    override suspend fun logWear(event: WearEvent): WearEventId {
        flow.value = flow.value + event
        return event.id
    }

    override suspend fun updateWear(event: WearEvent) {
        flow.value = flow.value.map { if (it.id == event.id) event else it }
    }

    override suspend fun deleteEvent(id: WearEventId) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun clearDay(date: java.time.LocalDate) {
        flow.value = flow.value.filterNot { it.date == date }
    }

    override suspend fun duplicateDay(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
    ) {
        val copies = flow.value.filter { it.date == from }.map { it.copy(id = WearEventId(0), date = to) }
        flow.value = flow.value + copies
    }
}

class FakeCategoryRepository(
    initial: List<Category> = emptyList(),
) : CategoryRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        parentId: CategoryId?,
    ): CategoryId = CategoryId(0)

    override suspend fun delete(id: CategoryId) = Unit
}

class FakeBrandRepository(
    initial: List<Brand> = emptyList(),
) : BrandRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Brand>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        logoUri: String?,
    ): BrandId = BrandId(0)
}

class FakeOccasionRepository(
    initial: List<Occasion> = emptyList(),
) : OccasionRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Occasion>> = flow.asStateFlow()

    override suspend fun create(name: String): OccasionId = OccasionId(0)
}

class FakeTagRepository(
    initial: List<Tag> = emptyList(),
) : TagRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Tag>> = flow.asStateFlow()

    override suspend fun create(name: String): TagId = TagId(0)

    override suspend fun delete(id: TagId) = Unit
}

class FakeStyleRuleRepository(
    initial: List<StyleRule> = emptyList(),
) : StyleRuleRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeActiveRules(): Flow<List<StyleRule>> = flow.map { rules -> rules.filter { it.isActive } }

    override suspend fun recordFeedback(feedback: Feedback): StyleRule? = null

    override suspend fun addUserRule(rule: StyleRule) {
        flow.value = flow.value + rule
    }

    override suspend fun deleteRule(id: com.wardrobe.app.core.model.common.StyleRuleId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

/** Always returns [suggestions] verbatim, regardless of [SuggestionContext] —
 * `RecommendationsViewModel` tests only need to verify it wires the engine's
 * output into UI state correctly, not re-verify the rule engine itself
 * (that's [com.wardrobe.app.core.data.repository.styling.RecommendationRuleEngineTest]'s job). */
class FakeStylingEngineRepository(
    var suggestions: List<ScoredOutfit> = emptyList(),
    var replacementId: GarmentId? = null,
) : StylingEngineRepository {
    var lastReplaceSlot: OutfitSlot? = null

    override suspend fun suggestOutfits(context: SuggestionContext): List<ScoredOutfit> = suggestions

    override suspend fun suggestForItem(
        garmentId: GarmentId,
        context: SuggestionContext,
    ): List<ScoredOutfit> = suggestions

    override suspend fun suggestReplacementForSlot(
        outfit: Outfit,
        slot: OutfitSlot,
        context: SuggestionContext,
    ): GarmentId? {
        lastReplaceSlot = slot
        return replacementId
    }

    override suspend fun suggestReplacementForAccessory(
        outfit: Outfit,
        category: AccessoryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId? = replacementId

    override suspend fun suggestReplacementForJewelry(
        outfit: Outfit,
        category: JewelryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId? = replacementId

    var diagnostics = RecommendationRunDiagnostics()

    override fun lastRunDiagnostics(): RecommendationRunDiagnostics = diagnostics
}

class FakeWardrobeIntelligenceRepository : WardrobeIntelligenceRepository {
    var garmentInsights: GarmentInsights? = null
    var outfitInsights: OutfitInsights? = null
    var capsule: CapsuleSuggestion = CapsuleSuggestion(CapsuleType.CASUAL, emptyMap(), "")
    var healthScore: WardrobeHealthScore =
        WardrobeHealthScore(score = 0, rotationScore = 0, usagePercent = 0, forgottenCount = 0)
    var dailyBrief: DailyBrief =
        DailyBrief(
            greeting = "",
            weather = null,
            todaysOccasionName = null,
            recommendation = null,
            explanations = emptyList(),
        )

    override fun observeGarmentInsights(garmentId: GarmentId): Flow<GarmentInsights?> = flowOf(garmentInsights)

    override fun observeOutfitInsights(outfitId: OutfitId): Flow<OutfitInsights?> = flowOf(outfitInsights)

    override fun observeWardrobeAlerts(): Flow<WardrobeAlerts> =
        flowOf(WardrobeAlerts(forgotten = emptyList(), overused = emptyList(), neverWorn = emptyList()))

    override fun observeShoppingGaps(): Flow<List<ShoppingGapSuggestion>> = flowOf(emptyList())

    override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())

    override suspend fun suggestCapsule(type: CapsuleType): CapsuleSuggestion = capsule

    override fun observeWardrobeHealthScore(): Flow<WardrobeHealthScore> = flowOf(healthScore)

    override fun observeCalendarConflicts(lookAheadDays: Int): Flow<List<CalendarConflict>> = flowOf(emptyList())

    override suspend fun buildDailyBrief(
        today: LocalDate,
        greeting: String,
    ): DailyBrief = dailyBrief.copy(greeting = greeting)
}

class FakeStylistPreferencesRepository(
    initial: RecommendationPreferences = RecommendationPreferences(),
) : StylistPreferencesRepository {
    val flow = MutableStateFlow(initial)

    override fun observePreferences(): Flow<RecommendationPreferences> = flow.asStateFlow()

    override suspend fun setPreferences(preferences: RecommendationPreferences) {
        flow.value = preferences
    }
}
