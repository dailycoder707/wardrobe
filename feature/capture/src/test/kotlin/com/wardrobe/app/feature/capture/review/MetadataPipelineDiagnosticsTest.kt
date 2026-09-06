package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val topsId = CategoryId(1)
private val CATEGORIES = listOf(Category(topsId, "Tops", null, CategoryLevel.TOP))
private val blueColorId = ColorId(1)

private fun emptyReference() =
    ReviewReferenceData(
        categories = CATEGORIES,
        brands = emptyList(),
        colors = listOf(Color(blueColorId, "Blue", "#0000FF")),
        materials = emptyList(),
        fabrics = emptyList(),
        occasions = emptyList(),
        tags = emptyList(),
    )

private fun suggestion(
    field: MetadataField,
    value: String,
    confidence: Float?,
    source: AiResultSource = AiResultSource.ON_DEVICE,
) = MetadataSuggestion(field, value, confidence, AiResultProvenance(source, null, null, null, Instant.EPOCH))

/**
 * M23 — proves the real-device diagnostic dump ("what did the model
 * actually return for this photo?") reports the true funnel state per
 * field: returned-and-resolved, returned-but-unresolved, and not-returned
 * (with the correct reason), never a fabricated or silently-dropped line.
 */
class MetadataPipelineDiagnosticsTest {
    @Test
    fun `a resolved HIGH-confidence suggestion is reported with its value, confidence, and resolved true`() {
        val form = GarmentMetadataFormState(primaryColorId = blueColorId)
        val diagnostics =
            formatMetadataPipelineDiagnostics(
                AiResultSource.ON_DEVICE,
                listOf(suggestion(MetadataField.PRIMARY_COLOR, "Blue", 0.9f)),
                form,
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(diagnostics.contains("PRIMARY_COLOR: value=\"Blue\" confidence=90% tier=HIGH resolved=true"))
    }

    @Test
    fun `a field genuinely unsupported by the on-device engine is reported NOT_RETURNED with reason NOT_SUPPORTED`() {
        val diagnostics =
            formatMetadataPipelineDiagnostics(
                AiResultSource.ON_DEVICE,
                emptyList(),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(diagnostics.contains("MATERIAL: NOT_RETURNED reason=NOT_SUPPORTED"))
        assertTrue(diagnostics.contains("FABRIC: NOT_RETURNED reason=NOT_SUPPORTED"))
    }

    @Test
    fun `a field the on-device engine supports but didn't detect this time is reported NOT_DETECTED`() {
        val diagnostics =
            formatMetadataPipelineDiagnostics(
                AiResultSource.ON_DEVICE,
                emptyList(),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(diagnostics.contains("PATTERN: NOT_RETURNED reason=NOT_DETECTED"))
    }

    @Test
    fun `a HIGH-confidence suggestion that fails resolution reports resolved false, not silently dropped`() {
        val diagnostics =
            formatMetadataPipelineDiagnostics(
                AiResultSource.ON_DEVICE,
                listOf(suggestion(MetadataField.PRIMARY_COLOR, "Chartreuse", 0.95f)),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(diagnostics.contains("PRIMARY_COLOR: value=\"Chartreuse\" confidence=95% tier=HIGH resolved=false"))
    }

    @Test
    fun `a suggestion with no real confidence reports confidence none rather than a fabricated number`() {
        val diagnostics =
            formatMetadataPipelineDiagnostics(
                AiResultSource.ON_DEVICE,
                listOf(suggestion(MetadataField.BRAND, "Acme", null)),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(diagnostics.contains("BRAND: value=\"Acme\" confidence=none tier=null resolved=false"))
    }

    @Test
    fun `the header names the source that ran, or UNKNOWN when nothing has summarized yet`() {
        val cloud =
            formatMetadataPipelineDiagnostics(
                AiResultSource.CLOUD,
                emptyList(),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )
        val unknown =
            formatMetadataPipelineDiagnostics(
                null,
                emptyList(),
                GarmentMetadataFormState(),
                emptyReference(),
                CATEGORIES,
            )

        assertTrue(cloud.startsWith("Metadata pipeline diagnostics — source=CLOUD"))
        assertTrue(unknown.startsWith("Metadata pipeline diagnostics — source=UNKNOWN"))
        assertFalse(unknown.contains("NOT_SUPPORTED"))
    }
}
