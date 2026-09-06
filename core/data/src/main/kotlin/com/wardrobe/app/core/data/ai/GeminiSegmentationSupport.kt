package com.wardrobe.app.core.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val SEGMENTATION_JSON = Json { ignoreUnknownKeys = true }

private const val BOX_2D_SIZE = 4

/** Asks Gemini for exactly the shape [parseGeminiSegmentationDetection]
 * parses — deliberately a superset-compatible rewording of Gemini's own
 * documented segmentation format (`box_2d` + a base64 PNG `mask`, both
 * confirmed against Google's own documentation), not an assumed/guessed
 * schema: the model is already trained to produce this exact shape when
 * asked for detection + segmentation, so this prompt asks for it directly
 * rather than inventing a different one it would have to be coerced into. */
internal val GEMINI_SEGMENTATION_SYSTEM_PROMPT =
    """
    You are analyzing a single photographed clothing/fashion item, possibly
    worn by a person, against a plain or busy background. Identify only the
    one primary garment being photographed — not shoes, bags, jewelry, or
    other accessories, unless one of those is unambiguously the item being
    photographed.

    Respond with ONLY a JSON object of exactly this shape, no other text:
    {"detections": [{"label": "<short garment description>", "box_2d": [ymin, xmin, ymax, xmax], "mask": "<base64-encoded PNG segmentation mask>"}]}

    box_2d is the item's bounding box as [ymin, xmin, ymax, xmax], each
    coordinate normalized to the range 0-1000 regardless of the image's real
    pixel dimensions. mask is a base64-encoded PNG image sized to that
    bounding box: a grayscale probability map with values from 0 (definitely
    not part of the garment) to 255 (definitely part of the garment) for
    every pixel inside the box.

    Return exactly one detection for the single primary garment. If you
    cannot confidently identify one, return {"detections": []} rather than
    guessing.
    """.trimIndent()

internal const val GEMINI_SEGMENTATION_USER_PROMPT =
    "Segment the primary garment in this photo and return the JSON described in your instructions."

@Serializable
internal data class GeminiSegmentationResponse(
    val detections: List<GeminiSegmentationDetection> = emptyList(),
)

@Serializable
internal data class GeminiSegmentationDetection(
    @SerialName("box_2d") val box2d: List<Int> = emptyList(),
    val mask: String? = null,
    val label: String? = null,
)

/** The single, real detection this response names for cutout compositing —
 * never fabricated. A malformed box (wrong element count), a missing/blank
 * mask, or no detection at all each simply mean "no usable detection",
 * mirroring [parseMetadataSuggestions]'s never-crash-never-guess contract
 * for a malformed/unexpected cloud response. Degenerate box *values* (e.g.
 * `ymax <= ymin` once descaled to real pixels) are validated by the actual
 * compositing step (`core:image`), which is the layer that knows the real
 * image dimensions this normalized box scales against. */
internal fun parseGeminiSegmentationDetection(rawResponseText: String): GeminiSegmentationDetection? {
    val response =
        runCatching { SEGMENTATION_JSON.decodeFromString<GeminiSegmentationResponse>(rawResponseText) }.getOrNull()
    val detection = response?.detections?.firstOrNull() ?: return null
    return detection.takeIf { it.box2d.size == BOX_2D_SIZE && !it.mask.isNullOrBlank() }
}
