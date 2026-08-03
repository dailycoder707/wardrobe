package com.wardrobe.app.core.model.intelligence

/**
 * A single composite 0..100 number for Home's "Wardrobe Health Score"/
 * "Rotation Score" cards — an explicitly labeled heuristic blend (usage
 * breadth + how evenly the wardrobe rotates + how many items have gone
 * forgotten), not a scientifically validated metric. See
 * `WardrobeIntelligenceRepositoryImpl`'s KDoc for the exact weighting.
 */
data class WardrobeHealthScore(
    val score: Int,
    val rotationScore: Int,
    val usagePercent: Int,
    val forgottenCount: Int,
)
