package com.wardrobe.app.core.domain.profile

/** M15 Part 4 — a display name is never saved unvalidated: trimmed,
 * rejected if that leaves nothing, capped at a reasonable length. Length is
 * measured in UTF-16 code units via [String.length] (never transliterated
 * or truncated byte-wise), so a Unicode name (accents, CJK, emoji) is
 * preserved exactly as typed rather than mangled to fit an ASCII
 * assumption.
 *
 * Lives in `core:domain` (not `feature:settings`) as of M16 so both the
 * Profile screen (`feature:settings`) and Onboarding (`feature:onboarding`)
 * validate a name identically — one function, not two copies that could
 * drift.
 */
const val MAX_DISPLAY_NAME_LENGTH = 50

sealed interface NameValidationResult {
    data class Valid(
        val trimmed: String,
    ) : NameValidationResult

    data object Blank : NameValidationResult

    data class TooLong(
        val maxLength: Int,
    ) : NameValidationResult
}

fun validateName(raw: String): NameValidationResult {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> NameValidationResult.Blank
        trimmed.length > MAX_DISPLAY_NAME_LENGTH -> NameValidationResult.TooLong(MAX_DISPLAY_NAME_LENGTH)
        else -> NameValidationResult.Valid(trimmed)
    }
}

/** The message shown under the name field — kept alongside [validateName]
 * itself so no caller composes its own wording for the same result. */
fun NameValidationResult.errorMessageOrNull(): String? =
    when (this) {
        is NameValidationResult.Valid -> null
        NameValidationResult.Blank -> "Name can't be empty."
        is NameValidationResult.TooLong -> "Name must be $maxLength characters or fewer."
    }
