package com.wardrobe.app.core.ai.gateway.adapter

import okhttp3.Interceptor
import okhttp3.Response

internal const val GEMINI_API_KEY_HEADER = "x-goog-api-key"
private const val GEMINI_API_KEY_QUERY_PARAM = "key"

/**
 * M24 real-device finding: `GeminiAdapter` sends the API key via the
 * `x-goog-api-key` header (a deliberate RC2 security choice — see that
 * class's KDoc — so `HttpLoggingInterceptor`'s URL logging never captures
 * it). Real-device testing against a live Gemini API key found that header
 * consistently rejected with `404` on `:generateContent` for that key/
 * project. `?key=` query-param auth is Google's other documented auth
 * transport for this API and is what a manual `GET /v1beta/models?key=...`
 * call against the same key succeeded with — so this interceptor moves the
 * key there instead, on the reasonable chance the same transport is what
 * `:generateContent` needs too for this key/project.
 *
 * **Not yet confirmed to fix the underlying `404`** — a real-device retest
 * after adding this interceptor still returned `404` even with the key
 * correctly present as a query parameter, meaning the root cause is likely
 * a Google-side restriction on this specific key/project (e.g.
 * `generateContent` not enabled the same way listing is) rather than an
 * auth-transport format issue this app can resolve in code. This
 * interceptor is kept because query-param is a real, documented,
 * Google-supported transport and a strictly safe thing to try — not
 * because it was proven to be *the* fix.
 *
 * Moves the value from [GEMINI_API_KEY_HEADER] into the
 * [GEMINI_API_KEY_QUERY_PARAM] query parameter, and does nothing at all to
 * a request that doesn't carry that header, so every other vendor is
 * unaffected. **Must be registered via `OkHttpClient.Builder.addNetworkInterceptor`,
 * not `addInterceptor`** — an application interceptor (which is what
 * `HttpLoggingInterceptor` also is) operates purely at the logical-call
 * level; its own response-side log line was found, on a real device, to
 * still reflect the key even when this interceptor ran *after* it as
 * another application interceptor. A network interceptor sits below that
 * entire layer and is structurally invisible to it — confirmed by
 * [GeminiQueryParamAuthInterceptorTest] using the real `HttpLoggingInterceptor`
 * class, not a hand-rolled stand-in for it.
 */
internal class GeminiQueryParamAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = request.header(GEMINI_API_KEY_HEADER) ?: return chain.proceed(request)
        val rewritten =
            request
                .newBuilder()
                .removeHeader(GEMINI_API_KEY_HEADER)
                .url(
                    request.url
                        .newBuilder()
                        .addQueryParameter(GEMINI_API_KEY_QUERY_PARAM, apiKey)
                        .build(),
                ).build()
        return chain.proceed(rewritten)
    }
}
