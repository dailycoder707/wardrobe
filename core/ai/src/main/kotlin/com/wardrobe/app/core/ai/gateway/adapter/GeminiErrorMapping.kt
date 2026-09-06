package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429

private const val API_KEY_INVALID_STATUS = "API_KEY_INVALID"
private const val REDACTED = "<redacted>"

/** The one label callers branch on — a configured model this key cannot
 * call is the only failure this app can help the user actually resolve, by
 * naming the models the key *can* call ([GeminiAdapter]). */
internal const val MODEL_NOT_FOUND_LABEL = "model_not_found"

/**
 * Google's real documented error envelope — every non-2xx response from
 * `generativelanguage.googleapis.com` carries one, and its `message` is the
 * only thing that distinguishes the several very different conditions this
 * API reports as a bare `404`.
 */
@Serializable
private data class GeminiErrorEnvelope(
    val error: GeminiErrorBody? = null,
)

@Serializable
private data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Turns an [HttpException] from [GeminiService] into a reason string the
 * Settings screen can actually act on (ADR-012 § Settings UI).
 *
 * Before this, every failure collapsed to `http_error_<code>`, which made a
 * real `404` indistinguishable between its two genuinely different causes:
 * a *route* that doesn't exist (wrong Base URL / a mangled `:generateContent`
 * path) and a *model* that the caller's key can't reach ("models/X is not
 * found for API version v1beta, or is not supported for generateContent").
 * Google returns a precise message for both; discarding it is what made the
 * live 404 undiagnosable from the device.
 *
 * [apiKey] is passed only so it can be **removed** from anything echoed back:
 * `GeminiQueryParamAuthInterceptor` moves the key into the request URL, and
 * some Google error paths quote the offending URL in `message`. Redacting
 * here means a vendor response can never turn into a key leak in the UI, a
 * metric row, or a crash report — the same guarantee `AiNetworkModule`'s
 * logging wiring makes for Logcat.
 */
internal fun geminiFailureReason(
    error: HttpException,
    apiKey: String,
): String {
    val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let(::parseErrorBody)
    return buildReason(error.code(), parsed?.status, parsed?.message?.redacting(apiKey))
}

/** A non-2xx body is not guaranteed to be Gemini's JSON envelope at all —
 * a proxy or a wrong host can return HTML. Failing to parse is normal here,
 * never an error worth propagating: the HTTP code alone still labels it. */
private fun parseErrorBody(body: String): GeminiErrorBody? =
    runCatching { errorJson.decodeFromString(GeminiErrorEnvelope.serializer(), body).error }.getOrNull()

private fun buildReason(
    code: Int,
    status: String?,
    message: String?,
): String {
    val label =
        when {
            isInvalidApiKey(code, status) -> "invalid_api_key"
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> "auth_failed"
            code == HTTP_NOT_FOUND && isModelNotFound(message) -> MODEL_NOT_FOUND_LABEL
            code == HTTP_TOO_MANY_REQUESTS -> "rate_limited"
            else -> "http_error_$code"
        }
    return if (message.isNullOrBlank()) label else "$label: $message"
}

/** Google reports a rejected key as `400 INVALID_ARGUMENT` with an
 * `API_KEY_INVALID` reason, not as `401` — matching on the code alone would
 * mislabel it as a malformed request. */
private fun isInvalidApiKey(
    code: Int,
    status: String?,
): Boolean = code == HTTP_BAD_REQUEST && status == API_KEY_INVALID_STATUS

/**
 * Google phrases a model-level 404 several different ways — the two
 * documented ones ("is not found for API version v1beta", "is not supported
 * for generateContent") plus, observed live on a real key, "This model
 * models/… is no longer available to new users." Matching only the
 * documented wording classified that third case as a generic route error,
 * which suppressed the model-list lookup that makes it fixable.
 *
 * The reliable common factor is that a model-level message always names the
 * offending model as a `models/…` resource, while a genuine route 404 ("The
 * requested URL was not found") never does — so that reference, not any one
 * sentence, is what this matches on.
 */
private fun isModelNotFound(message: String?): Boolean = message != null && message.contains("models/")

private fun String.redacting(apiKey: String): String = if (apiKey.isBlank()) this else replace(apiKey, REDACTED)
