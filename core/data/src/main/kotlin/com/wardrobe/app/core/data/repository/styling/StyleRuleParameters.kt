package com.wardrobe.app.core.data.repository.styling

/**
 * [com.wardrobe.app.core.model.styling.StyleRule.parametersJson] is a deliberately
 * simple flat `key=value;key2=value2` blob, not general JSON, despite the field's
 * name — every rule type this engine currently supports needs at most two scalar
 * values, so a tiny hand-rolled parser avoids pulling a JSON dependency into
 * `core:data` for that. If a future rule type needs nested/structured parameters,
 * that's the trigger to introduce real JSON, not before (see
 * phase-6-personal-wardrobe-stylist.md's Known Limitations).
 */
internal fun encodeRuleParameters(params: Map<String, String>): String =
    params.entries.joinToString(";") { "${it.key}=${it.value}" }

internal fun decodeRuleParameters(raw: String): Map<String, String> =
    raw
        .split(";")
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
