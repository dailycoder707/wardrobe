package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.ai.AiResultSource

/**
 * The domain-safe result of [com.wardrobe.app.core.domain.repository.VirtualTryOnRenderRepository.render]
 * (M12) — a plain file path rather than
 * [com.wardrobe.app.core.ai.tryon.TryOnRenderResult]'s in-memory `Bitmap`,
 * since `core:domain` is pure Kotlin/JVM and must never depend on Android
 * types. [source] tells the Try-On Review screen whether this render came
 * from the on-device pipeline or a configured cloud provider, so its
 * Original/On-Device/Cloud comparison can label each tab honestly.
 */
sealed interface VirtualTryOnRenderOutcome {
    data class Success(
        val renderedImagePath: String,
        val confidence: Float?,
        val source: AiResultSource,
    ) : VirtualTryOnRenderOutcome

    data class Failure(
        val reason: String,
    ) : VirtualTryOnRenderOutcome
}
