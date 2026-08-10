package com.wardrobe.app.feature.outfits.recommendations

import org.junit.Assert.assertEquals
import org.junit.Test

/** [String.toReasonBullets] backs the "Why this?" list (M19 Part 7) — proves
 * it only ever re-splits the engine's own real explanation text, never
 * invents or drops a reason. */
class RecommendationUiMappersTest {
    @Test
    fun `splits a multi-sentence explanation into one bullet per real reason`() {
        val explanation = "It's one of your favorites. It matches your plans for today. The colors work together."

        val bullets = explanation.toReasonBullets()

        assertEquals(
            listOf(
                "It's one of your favorites",
                "It matches your plans for today",
                "The colors work together",
            ),
            bullets,
        )
    }

    @Test
    fun `a single-sentence explanation becomes a single bullet, not fragmented`() {
        val bullets = "A complete outfit built from what's currently available.".toReasonBullets()

        assertEquals(listOf("A complete outfit built from what's currently available"), bullets)
    }

    @Test
    fun `an empty explanation produces no bullets, never a fabricated placeholder`() {
        assertEquals(emptyList<String>(), "".toReasonBullets())
    }
}
