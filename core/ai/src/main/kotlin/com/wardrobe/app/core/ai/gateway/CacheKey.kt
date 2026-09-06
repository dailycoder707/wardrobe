package com.wardrobe.app.core.ai.gateway

import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiVendor

private const val MAX_FILENAME_LENGTH = 200

/** `imageSha256:capability:provider:model:promptVersion` (ADR-012 §4) —
 * identical inputs always produce the identical key, so the Gateway never
 * repeats an identical paid request for any reason (retry, resume, or the
 * user simply reopening a draft). */
internal fun buildCacheKey(
    imageSha256: String,
    capability: AiCapability,
    vendor: AiVendor?,
    model: String?,
    promptVersion: String,
): String {
    val parts = listOf(imageSha256, capability.name, vendor?.name ?: "none", model ?: "none", promptVersion)
    return parts.joinToString(":")
}

/** Cache keys can contain characters unsafe for a filename (`:`) — this
 * doesn't need to be reversible, only stable and collision-free per key. */
internal fun sanitizeForFileName(key: String): String =
    key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(MAX_FILENAME_LENGTH)
