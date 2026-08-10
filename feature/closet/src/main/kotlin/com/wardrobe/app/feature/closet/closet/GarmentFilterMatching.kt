package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import java.time.LocalDate

private const val RECENTLY_WORN_DAYS = 30L

/**
 * The single, pure evaluation of whether [this] garment matches [filters] —
 * consolidates what was previously split between a SQL `WHERE` pushdown
 * (`GarmentRepositoryImpl`) and a separate in-memory pass in `ClosetViewModel`
 * into one deterministic, fully unit-testable function (M17 architecture
 * decision, see ADR-016). `GarmentRepository`/`GarmentFilter`/`GarmentDao`
 * are unchanged — this only affects how `feature:closet` narrows the
 * already-loaded, search-filtered garment list.
 *
 * Every non-empty [Set] facet is OR'd internally (any one of the selected
 * values is enough) and every facet is AND'd against every other facet —
 * `(colors) AND (seasons) AND (dressCodes) AND ...`. Expressed as a list of
 * independent per-facet checks rather than a chain of early returns, so no
 * single function accumulates the whole facet count's cyclomatic complexity.
 */
fun Garment.matchesClosetFilters(
    filters: ClosetFilterState,
    categoriesById: Map<CategoryId, Category>,
    wearStats: CostPerWearEntry?,
    today: LocalDate,
): Boolean =
    listOf(
        matchesCategory(filters.categories, categoriesById),
        matchesColor(filters.colors),
        matchesSet(filters.brands) { brandId in filters.brands },
        matchesAny(filters.materials, materials.map { it.material.id }),
        matchesAny(filters.fabrics, fabrics.map { it.fabric.id }),
        matchesAny(filters.seasons, seasons),
        matchesAny(filters.dressCodes, dressCodes),
        matchesAny(filters.occasions, occasionIds),
        matchesAny(filters.tags, tagIds),
        matchesSet(filters.fits) { fit in filters.fits },
        matchesSet(filters.genders) { gender in filters.genders },
        matchesSet(filters.waterproofLevels) { waterproofLevel in filters.waterproofLevels },
        !filters.favoriteOnly || isFavorite,
        matchesPriceRange(filters.priceMin, filters.priceMax),
        !filters.neverWorn || (wearStats?.totalWearCount ?: 0) == 0,
        !filters.recentlyWornOnly || isRecentlyWorn(wearStats, today),
    ).all { it }

/** A [Category] selection matches a garment either directly, or (for a
 * top-level category) via any of its subcategories — real hierarchy-based
 * refinement using [Category.parentId], not a fabricated grouping. */
private fun Garment.matchesCategory(
    selected: Set<CategoryId>,
    categoriesById: Map<CategoryId, Category>,
): Boolean =
    selected.isEmpty() ||
        categoryId in selected ||
        categoriesById[categoryId]?.parentId in selected

private fun Garment.matchesColor(selected: Set<ColorId>): Boolean =
    selected.isEmpty() || primaryColorId in selected || secondaryColorId in selected

/** An empty facet selection never filters anything out; otherwise at least
 * one of the garment's own values for that facet must be selected — the OR-
 * within-a-facet half of the AND/OR contract. */
private fun <T> matchesAny(
    selected: Set<T>,
    garmentValues: Collection<T>,
): Boolean = selected.isEmpty() || garmentValues.any { it in selected }

/** Same OR-within-a-facet contract as [matchesAny], for single-valued
 * (nullable) garment attributes where membership can't be expressed as a
 * plain `Collection<T>` lookup (the check itself already captures nullability). */
private fun <T> matchesSet(
    selected: Set<T>,
    isMember: () -> Boolean,
): Boolean = selected.isEmpty() || isMember()

private fun Garment.matchesPriceRange(
    priceMin: Double?,
    priceMax: Double?,
): Boolean {
    val amount = price?.amount
    val aboveMin = priceMin == null || (amount != null && amount >= priceMin)
    val belowMax = priceMax == null || (amount != null && amount <= priceMax)
    return aboveMin && belowMax
}

private fun isRecentlyWorn(
    wearStats: CostPerWearEntry?,
    today: LocalDate,
): Boolean {
    val lastWorn = wearStats?.lastWornDate ?: return false
    return lastWorn.isAfter(today.minusDays(RECENTLY_WORN_DAYS))
}

/** Orders categories for the filter UI so each top-level category is
 * immediately followed by its own subcategories — real refinement of the
 * existing [Category] hierarchy, not a fabricated grouping. */
fun groupCategoriesForFilterUi(categories: List<Category>): List<Category> {
    val byId = categories.associateBy { it.id }

    fun groupKey(category: Category): String = category.parentId?.let { byId[it]?.name } ?: category.name
    return categories.sortedWith(
        compareBy(
            { groupKey(it).lowercase() },
            { it.parentId != null },
            { it.name.lowercase() },
        ),
    )
}

/** A category's display label in the filter UI — subcategories are shown as
 * "Parent · Sub" so the hierarchy is visible in an otherwise flat chip list. */
fun Category.displayLabel(categoriesById: Map<CategoryId, Category>): String {
    val parentName = parentId?.let { categoriesById[it]?.name }
    return if (parentName != null) "$parentName · $name" else name
}
