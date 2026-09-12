package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.SuggestionContext
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

private val DISPATCH_CONTEXT =
    AiDispatchContext(
        AiCapability.OUTFIT_STYLING,
        AiProviderConfig(
            capability = AiCapability.OUTFIT_STYLING,
            mode = AiProviderMode.CLOUD,
            vendor = AiVendor.GENERIC_REST,
            baseUrl = "https://example.test",
            model = null,
            costRatePerThousandTokens = null,
            consentGrantedAt = Instant.EPOCH,
            consentHost = "https://example.test",
        ),
        "a-real-key",
    )

@RunWith(RobolectricTestRunner::class)
class CloudStylingEngineTest {
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

    private val top = garment(1, 1)
    private val bottom = garment(2, 2)

    private fun input() =
        EngineInput(
            candidateGarments = listOf(top, bottom),
            garmentsById = listOf(top, bottom).associateBy { it.id },
            categoriesById =
                listOf(
                    Category(CategoryId(1), "Tops", null, CategoryLevel.TOP),
                    Category(CategoryId(2), "Bottoms", null, CategoryLevel.TOP),
                ).associateBy { it.id },
            colorsById = emptyMap(),
            activeRules = emptyList(),
            preferences = RecommendationPreferences(),
            costPerWearByGarmentId = emptyMap(),
            favoriteColorIds = emptyList(),
            today = LocalDate.of(2026, 6, 15),
            weather = null,
        )

    private fun provenance() = AiResultProvenance(AiResultSource.CLOUD, "generic", null, "styling-v1", Instant.EPOCH)

    @Test
    fun `a well-formed cloud response produces a validated outfit`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val rawResponse =
                """{"outfits":[{"picks":{"TOP":1,"BOTTOM":2},"reasoning":"casual","confidence":0.8}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(rawResponse, provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertEquals(1, result.size)
            assertEquals(
                2,
                result
                    .first()
                    .outfit.garments.size,
            )
            assertEquals(0.8f, result.first().aiConfidence)
        }

    @Test
    fun `malformed JSON never fabricates an outfit`() =
        runTest {
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success("not json at all", provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `a dispatch failure returns no outfits rather than throwing`() =
        runTest {
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Failure("timeout")
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `a hallucinated garment id in the response is dropped, never surfaced`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val rawResponse =
                """{"outfits":[{"picks":{"TOP":999,"BOTTOM":2},"reasoning":"bad id","confidence":0.8}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(rawResponse, provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertTrue(result.isEmpty())
        }

    /** The Gateway's cache key is `sha256(image):capability:vendor:model:promptVersion`
     * (`CacheKey.kt`) — the system prompt, which is where the anchor actually
     * lives, is not part of it. So the fingerprint bitmap this engine sends as
     * the "image" is the *only* thing that can distinguish an anchored request
     * from an unanchored one. If it doesn't, `suggestOutfits` and
     * `suggestForItem` collide on one cache row and the anchored call is served
     * outfits that were never built around its anchor — every one of which
     * `validateCloudOutfit` then rejects, silently dropping the user to the
     * on-device engine. */
    private fun imageDigest(bitmap: Bitmap): String {
        val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun cacheKeyImageDigestFor(anchor: GarmentId?): String {
        val gateway = mockk<AiGateway>()
        val captured = slot<Bitmap>()
        coEvery {
            gateway.runVisionPrompt(any(), any(), any(), any(), capture(captured), any())
        } returns VisionPromptResult.Success("""{"outfits":[]}""", provenance())
        CloudStylingEngine(gateway).suggestOutfits(
            context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
            input = input(),
            dispatchContext = DISPATCH_CONTEXT,
            inspirationImage = null,
            count = 3,
            anchorGarmentId = anchor,
        )
        return imageDigest(captured.captured)
    }

    @Test
    fun `an anchored request never shares a cache key with an unanchored one`() =
        runTest {
            val unanchored = cacheKeyImageDigestFor(null)
            val anchored = cacheKeyImageDigestFor(GarmentId(1))

            assertNotEquals(unanchored, anchored)
        }

    @Test
    fun `two differently-anchored requests never share a cache key`() =
        runTest {
            val anchoredOnTop = cacheKeyImageDigestFor(GarmentId(1))
            val anchoredOnBottom = cacheKeyImageDigestFor(GarmentId(2))

            assertNotEquals(anchoredOnTop, anchoredOnBottom)
        }

    @Test
    fun `an unparseable pick value drops that outfit rather than throwing`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val rawResponse =
                """{"outfits":[{"picks":{"TOP":{"id":1},"BOTTOM":2},"reasoning":"nested","confidence":0.8}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(rawResponse, provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `an outfits array holding a bare string drops that entry rather than throwing`() =
        runTest {
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success("""{"outfits":["a blue shirt with jeans"]}""", provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertTrue(result.isEmpty())
        }

    /** An unusable `reasoning` is not a reason to throw away a structurally
     * valid outfit — the picks are what the user actually gets, and a missing
     * explanation already has a defined fallback. The outfit survives with
     * that fallback text rather than the whole response failing. */
    @Test
    fun `a non-primitive reasoning field keeps the outfit and falls back to the default explanation`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val rawResponse =
                """{"outfits":[{"picks":{"TOP":1,"BOTTOM":2},"reasoning":["why"],"confidence":0.8}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(rawResponse, provenance())
            val engine = CloudStylingEngine(gateway)

            val result =
                engine.suggestOutfits(
                    context = SuggestionContext(LocalDate.of(2026, 6, 15), null, null),
                    input = input(),
                    dispatchContext = DISPATCH_CONTEXT,
                    inspirationImage = null,
                    count = 3,
                )

            assertEquals(1, result.size)
            assertEquals("Suggested by AI styling.", result.first().explanation)
        }
}
