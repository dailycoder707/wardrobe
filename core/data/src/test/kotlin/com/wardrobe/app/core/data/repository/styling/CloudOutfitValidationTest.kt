package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 6, 15)
private val PROVENANCE =
    AiResultProvenance(AiResultSource.CLOUD, "openai", "gpt-vision", "styling-v1", Instant.EPOCH)

class CloudOutfitValidationTest {
    private fun garment(
        id: Long,
        categoryId: Long,
    ) = Garment(
        id = GarmentId(id),
        name = "Item $id",
        categoryId = CategoryId(categoryId),
        primaryColorId = null,
        palette = emptyList(),
        materials = emptyList(),
        tagIds = emptyList(),
        seasons = emptySet(),
        dressCodes = emptySet(),
        pattern = null,
        fit = null,
        length = null,
        sleeveLength = null,
        warmthRating = null,
        breathabilityRating = null,
        brandId = null,
        size = null,
        price = null,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = false,
        images = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun category(
        id: Long,
        name: String,
    ) = Category(CategoryId(id), name, null, CategoryLevel.TOP)

    private val topCategory = category(1, "Tops")
    private val bottomCategory = category(2, "Bottoms")
    private val dressCategory = category(3, "Dresses")

    private val top = garment(1, 1)
    private val bottom = garment(2, 2)
    private val dress = garment(3, 3)

    private fun input(garments: List<Garment>) =
        EngineInput(
            candidateGarments = garments,
            garmentsById = garments.associateBy { it.id },
            categoriesById = listOf(topCategory, bottomCategory, dressCategory).associateBy { it.id },
            colorsById = emptyMap(),
            activeRules = emptyList(),
            preferences = RecommendationPreferences(),
            costPerWearByGarmentId = emptyMap(),
            favoriteColorIds = emptyList(),
            today = TODAY,
            weather = null,
        )

    @Test
    fun `a top+bottom pick that resolves to real candidates in their claimed slots validates`() {
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to top.id, OutfitSlot.BOTTOM to bottom.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "Casual and comfortable",
                confidence = 0.9f,
                provenance = PROVENANCE,
            )

        assertTrue(result != null)
        assertEquals(2, result!!.outfit.garments.size)
        assertEquals(PROVENANCE, result.provenance)
        assertEquals(0.9f, result.aiConfidence)
    }

    @Test
    fun `a dress-only pick validates without needing top and bottom`() {
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.DRESS to dress.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "A dress",
                confidence = null,
                provenance = PROVENANCE,
            )

        assertTrue(result != null)
    }

    @Test
    fun `a pick referencing a garment id absent from the wardrobe is rejected, never fabricated`() {
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to GarmentId(999), OutfitSlot.BOTTOM to bottom.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "Hallucinated id",
                confidence = 0.9f,
                provenance = PROVENANCE,
            )

        assertNull(result)
    }

    @Test
    fun `a pick that puts a garment in a slot it doesn't actually belong to is rejected`() {
        val result =
            validateCloudOutfit(
                // top.id is a TOP-slot garment, not BOTTOM — the model claimed the wrong slot.
                picks = mapOf(OutfitSlot.TOP to top.id, OutfitSlot.BOTTOM to top.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "Wrong slot",
                confidence = 0.9f,
                provenance = PROVENANCE,
            )

        assertNull(result)
    }

    @Test
    fun `the same garment id reused across two slots is rejected`() {
        val sameCategoryGarment = garment(4, 1)
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to top.id, OutfitSlot.BOTTOM to top.id),
                input = input(listOf(top, sameCategoryGarment, bottom, dress)),
                reasoning = "Duplicate",
                confidence = 0.9f,
                provenance = PROVENANCE,
            )

        assertNull(result)
    }

    @Test
    fun `a top with no bottom and no dress is rejected as an incomplete outfit`() {
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to top.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "Incomplete",
                confidence = 0.9f,
                provenance = PROVENANCE,
            )

        assertNull(result)
    }

    @Test
    fun `a required anchor garment missing from the picks is rejected`() {
        val otherTop = garment(5, 1)
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to otherTop.id, OutfitSlot.BOTTOM to bottom.id),
                input = input(listOf(top, otherTop, bottom, dress)),
                reasoning = "Doesn't include the anchor",
                confidence = 0.9f,
                provenance = PROVENANCE,
                requiredAnchor = top.id,
            )

        assertNull(result)
    }

    @Test
    fun `a required anchor garment present in the picks validates`() {
        val result =
            validateCloudOutfit(
                picks = mapOf(OutfitSlot.TOP to top.id, OutfitSlot.BOTTOM to bottom.id),
                input = input(listOf(top, bottom, dress)),
                reasoning = "Includes the anchor",
                confidence = 0.9f,
                provenance = PROVENANCE,
                requiredAnchor = top.id,
            )

        assertTrue(result != null)
    }
}
