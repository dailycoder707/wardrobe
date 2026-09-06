package com.wardrobe.app.core.ai.gateway.adapter

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M24 real-device finding: a live Gemini API key rejected header-based
 * (`x-goog-api-key`) auth on `:generateContent` with a plain `404`.
 * [GeminiQueryParamAuthInterceptor] moves the key from the header into a
 * `?key=` query parameter instead — these tests prove it does that
 * correctly and leaves every other request untouched.
 *
 * The RC2-safety test below uses the *real* `okhttp3.logging.HttpLoggingInterceptor`,
 * not a hand-rolled stand-in — an earlier version of this test used a
 * simple capturing lambda that only checked the request-side URL, which
 * passed while the real `HttpLoggingInterceptor`'s *response*-side log line
 * still leaked the key on a real device (its response line reflects the
 * request that was actually sent, not the pre-rewrite one, when the
 * rewriting interceptor is registered as another *application* interceptor
 * via `addInterceptor`). Using the real class here is what actually catches
 * that class of mistake instead of just asserting a simplified mental model
 * of how OkHttp's interceptor chain behaves.
 */
class GeminiQueryParamAuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithInterceptor(): OkHttpClient =
        OkHttpClient.Builder().addNetworkInterceptor(GeminiQueryParamAuthInterceptor()).build()

    @Test
    fun `moves the Gemini auth header into a key query parameter and removes the header`() {
        server.enqueue(MockResponse().setBody("{}"))
        val request =
            Request
                .Builder()
                .url(server.url("/v1beta/models/gemini-2.5-flash:generateContent"))
                .header(GEMINI_API_KEY_HEADER, "real-secret-key")
                .build()

        clientWithInterceptor().newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals("real-secret-key", recorded.requestUrl?.queryParameter("key"))
        assertNull(recorded.getHeader(GEMINI_API_KEY_HEADER))
    }

    /** Appending the key means rebuilding the URL, and Gemini's route ends in
     * a literal `:generateContent` — a rebuild that percent-encoded that
     * colon would be answered by Google with a bare `404` that looks
     * identical to a wrong Base URL. The rewrite must touch the query string
     * and nothing else. */
    @Test
    fun `the request path is left byte-for-byte unchanged by the rewrite`() {
        server.enqueue(MockResponse().setBody("{}"))
        val request =
            Request
                .Builder()
                .url(server.url("/v1beta/models/gemini-2.5-flash:generateContent"))
                .header(GEMINI_API_KEY_HEADER, "real-secret-key")
                .build()

        clientWithInterceptor().newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", recorded.requestUrl?.encodedPath)
        assertFalse(
            "a percent-encoded colon does not match Google's route",
            recorded.requestUrl?.encodedPath?.contains("%3A") == true,
        )
    }

    @Test
    fun `a request with no Gemini auth header passes through completely unchanged`() {
        server.enqueue(MockResponse().setBody("{}"))
        val request =
            Request
                .Builder()
                .url(server.url("/v1/chat/completions"))
                .header("Authorization", "Bearer some-other-vendor-key")
                .build()

        clientWithInterceptor().newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.requestUrl?.queryParameter("key"))
        assertEquals("Bearer some-other-vendor-key", recorded.getHeader("Authorization"))
    }

    /** RC2's actual guarantee, checked against the real logging class: when
     * registered as a *network* interceptor (matching `AiNetworkModule`'s
     * real wiring), neither the request-side nor the response-side log line
     * `HttpLoggingInterceptor.Level.BASIC` produces ever contains the key —
     * even though the key genuinely was sent, and genuinely does appear in
     * what the mock server received. */
    @Test
    fun `HttpLoggingInterceptor never logs the key on either the request or response line`() {
        server.enqueue(MockResponse().setBody("{}"))
        val loggedLines = mutableListOf<String>()
        val logging =
            HttpLoggingInterceptor { message -> loggedLines += message }.apply {
                level =
                    HttpLoggingInterceptor.Level.BASIC
            }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(logging)
                .addNetworkInterceptor(GeminiQueryParamAuthInterceptor())
                .build()
        val request =
            Request
                .Builder()
                .url(server.url("/v1beta/models/gemini-2.5-flash:generateContent"))
                .header(GEMINI_API_KEY_HEADER, "real-secret-key")
                .build()

        client.newCall(request).execute().close()

        assertTrue("expected both a request and a response log line", loggedLines.size >= 2)
        loggedLines.forEach { line ->
            assertFalse("log line leaked the key: $line", line.contains("real-secret-key"))
        }
        // The key genuinely was sent — this isn't passing because the
        // interceptor silently dropped it.
        assertEquals("real-secret-key", server.takeRequest().requestUrl?.queryParameter("key"))
    }
}
