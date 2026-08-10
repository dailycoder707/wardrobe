package com.wardrobe.app.core.model.ai

/**
 * The three-way bucketing the review screen renders a suggestion chip with
 * (Add-to-Wardrobe v2): `HIGH` auto-fills a field, `MEDIUM` shows a
 * dismissible "Suggested" chip, `LOW` (or no confidence at all — a provider
 * that doesn't return one) stays blank. Never fabricated: a suggestion with
 * no real confidence value has no tier at all ([forConfidence] returns
 * `null`), it does not default to `LOW` by assumption.
 */
enum class ConfidenceTier {
    HIGH,
    MEDIUM,
    LOW,
    ;

    companion object {
        /** Thresholds are this project's own editorial choice, not a vendor
         * convention — deliberately conservative so `HIGH` (auto-fill) only
         * applies when a provider is genuinely confident. */
        private const val HIGH_THRESHOLD = 0.85f
        private const val MEDIUM_THRESHOLD = 0.5f

        fun forConfidence(confidence: Float?): ConfidenceTier? =
            when {
                confidence == null -> null
                confidence >= HIGH_THRESHOLD -> HIGH
                confidence >= MEDIUM_THRESHOLD -> MEDIUM
                else -> LOW
            }
    }
}
