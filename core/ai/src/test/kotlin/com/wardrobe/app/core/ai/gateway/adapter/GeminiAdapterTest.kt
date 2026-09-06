package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BITMAP_SIZE = 4

@RunWith(RobolectricTestRunner::class)
class GeminiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GeminiAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GeminiAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run posts to the model-specific generateContent URL with the key as a header, never the URL`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"candidates":[{"content":{"parts":[{"text":"gemini-suggestion"}]}}],""" +
                        """"usageMetadata":{"promptTokenCount":8,"candidatesTokenCount":3}}""",
                ),
            )

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "gemini-key",
                        model = "gemini-1.5-flash",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Success)
            assertEquals("gemini-suggestion", result.rawResponseText)
            assertEquals(8, result.estimatedInputTokens)
            assertEquals(3, result.estimatedOutputTokens)
            val recorded = server.takeRequest()
            assertTrue(recorded.path?.startsWith("/v1beta/models/gemini-1.5-flash:generateContent") == true)
            // RC2 security fix: the key must never appear in the URL — every
            // OkHttpClient in this app logs the request URL at Level.BASIC, so a
            // key in the URL would be written to Logcat on every real call.
            assertTrue(recorded.path?.contains("gemini-key") == false)
            assertEquals("gemini-key", recorded.getHeader("x-goog-api-key"))
        }

    @Test
    fun `run requests Gemini's native JSON response mode only when the caller expects structured JSON`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
            val baseRequest =
                VisionPromptAdapterRequest(
                    baseUrl = server.url("/").toString(),
                    apiKey = "gemini-key",
                    model = "gemini-1.5-flash",
                    systemPrompt = "system",
                    userPrompt = "user",
                    image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                )

            adapter.run(baseRequest.copy(expectJsonResponse = true))
            val jsonRequestedBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            adapter.run(baseRequest.copy(expectJsonResponse = false))
            val promptOnlyBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

            assertEquals(
                "application/json",
                jsonRequestedBody["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content,
            )
            assertTrue("generationConfig must be absent when not requested", "generationConfig" !in promptOnlyBody)
        }

    @Test
    fun `run returns Failure rather than fabricating a result when no candidates are returned`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "gemini-key",
                        model = "gemini-1.5-flash",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Failure)
            assertEquals("no_content_in_response", result.reason)
        }

    private fun request(
        model: String? = "gemini-2.5-flash",
        apiKey: String = "gemini-key",
    ) = VisionPromptAdapterRequest(
        baseUrl = server.url("/").toString(),
        apiKey = apiKey,
        model = model,
        systemPrompt = "system",
        userPrompt = "user",
        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
    )

    /** [followUp] backs the model-list lookup the adapter makes *only* after
     * a "model not found" — every other failure must not trigger a second
     * call, which the `assertEquals(1, server.requestCount)` checks below
     * rely on. */
    private fun failureReasonFor(
        responseCode: Int,
        body: String,
        apiKey: String = "gemini-key",
        followUp: MockResponse? = null,
    ): String =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(responseCode).setBody(body))
            if (followUp != null) server.enqueue(followUp)
            val result = adapter.run(request(apiKey = apiKey))
            check(result is VisionPromptAdapterResult.Failure)
            result.reason
        }

    private fun modelNotFoundBody() =
        """{"error":{"code":404,"message":"models/gemini-9-ultra is not found for API version """ +
            """v1beta, or is not supported for generateContent.","status":"NOT_FOUND"}}"""

    /**
     * The end-to-end guarantee the previous tests could not make. The adapter
     * builds `…/models/{model}:generateContent`, but every real call then
     * passes through [GeminiQueryParamAuthInterceptor], which rebuilds the
     * URL to append `?key=`. Google routes on the literal `:generateContent`
     * suffix and answers a percent-encoded `%3AgenerateContent` with a bare
     * 404 — and because `HttpLoggingInterceptor` logs the *pre*-rewrite URL,
     * that mangling would look completely correct in Logcat. Asserting the
     * path the server actually receives, through the real interceptor, is
     * the only thing that covers it.
     */
    @Test
    fun `the model path reaches the server unescaped after the auth rewrite`() =
        runTest {
            val wiredAdapter =
                GeminiAdapter(
                    server.retrofitService(
                        OkHttpClient.Builder().addNetworkInterceptor(GeminiQueryParamAuthInterceptor()).build(),
                    ),
                )
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""))

            wiredAdapter.run(request(model = "gemini-2.5-flash"))

            val recorded = server.takeRequest()
            assertEquals(
                "/v1beta/models/gemini-2.5-flash:generateContent",
                recorded.requestUrl?.encodedPath,
            )
            assertFalse(
                "the colon must not be percent-encoded — Google 404s on %3A",
                recorded.requestUrl?.encodedPath?.contains("%3A") == true,
            )
            assertEquals("gemini-key", recorded.requestUrl?.queryParameter("key"))
        }

    @Test
    fun `run sends the image as an inline_data part alongside the text prompt`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""))

            adapter.run(request())

            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
            assertEquals("user", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
            val inlineData = parts[1].jsonObject["inline_data"]!!.jsonObject
            assertEquals("image/webp", inlineData["mime_type"]!!.jsonPrimitive.content)
            assertTrue(
                "the encoded image must not be empty",
                inlineData["data"]!!.jsonPrimitive.content.isNotEmpty(),
            )
        }

    @Test
    fun `a 404 naming a missing model is reported as model_not_found, not a bare http_error`() {
        val reason =
            failureReasonFor(
                responseCode = 404,
                body = modelNotFoundBody(),
                // The follow-up model lookup fails too: the original reason
                // must survive rather than be replaced by a second error.
                followUp = MockResponse().setResponseCode(404).setBody("{}"),
            )

        assertTrue(reason, reason.startsWith("model_not_found: "))
        assertTrue(reason, reason.contains("models/gemini-9-ultra"))
        assertFalse(reason, reason.contains("Models this API lists"))
    }

    /**
     * Task 7's actual product requirement: a model this key cannot call is
     * the one failure the app can make actionable, by naming the models it
     * *can* call. Anything without `generateContent` in its supported
     * methods (embedding models, for instance) must not be offered, and the
     * app must never invent a replacement model on the user's behalf.
     */
    @Test
    fun `a model_not_found failure names the generateContent models this key can actually call`() {
        val reason =
            failureReasonFor(
                responseCode = 404,
                body = modelNotFoundBody(),
                followUp =
                    MockResponse().setBody(
                        """{"models":[""" +
                            """{"name":"models/gemini-3-pro","supportedGenerationMethods":["generateContent"]},""" +
                            """{"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},""" +
                            """{"name":"models/gemini-3-flash","supportedGenerationMethods":""" +
                            """["generateContent","countTokens"]}]}""",
                    ),
            )

        assertTrue(reason, reason.contains("Models this API lists for generateContent"))
        assertTrue(reason, reason.contains("gemini-3-pro"))
        assertTrue(reason, reason.contains("gemini-3-flash"))
        assertFalse("embedding-only models must not be offered: $reason", reason.contains("text-embedding-004"))
        // The `models/` prefix is Google's resource naming, not what the
        // Settings Model field takes.
        assertFalse(reason, reason.contains("models/gemini-3-pro"))
    }

    /** The exact body Google returned for a real key on a real device — this
     * wording is not in Google's published error list, and matching only the
     * documented phrasings classified it as a generic route 404, which
     * suppressed the model-list lookup that makes it actionable. */
    @Test
    fun `the live 'no longer available to new users' 404 is classified as model_not_found`() {
        val reason =
            failureReasonFor(
                responseCode = 404,
                body =
                    """{"error":{"code":404,"message":"This model models/gemini-2.5-flash is no longer """ +
                        """available to new users. Please update your code to use a newer model for the """ +
                        """latest features and improvements.","status":"NOT_FOUND"}}""",
                followUp =
                    MockResponse().setBody(
                        """{"models":[{"name":"models/gemini-flash-latest",""" +
                            """"supportedGenerationMethods":["generateContent"]}]}""",
                    ),
            )

        assertTrue(reason, reason.startsWith("model_not_found: "))
        assertTrue(reason, reason.contains("gemini-flash-latest"))
    }

    @Test
    fun `the model lookup runs only for model_not_found, never for other failures`() {
        failureReasonFor(
            responseCode = 429,
            body = """{"error":{"code":429,"message":"Quota exceeded.","status":"RESOURCE_EXHAUSTED"}}""",
        )

        assertEquals("a rate-limit failure must not trigger a second call", 1, server.requestCount)
    }

    @Test
    fun `the model lookup reuses the configured base URL and never puts the key in the path`() {
        failureReasonFor(
            responseCode = 404,
            body = modelNotFoundBody(),
            followUp = MockResponse().setBody("""{"models":[]}"""),
        )

        server.takeRequest()
        val lookup = server.takeRequest()
        assertEquals("/v1beta/models", lookup.requestUrl?.encodedPath)
        assertEquals("gemini-key", lookup.requestUrl?.queryParameter("key") ?: lookup.getHeader("x-goog-api-key"))
    }

    @Test
    fun `a 404 from an unreachable route stays a plain http_error_404 carrying the vendor message`() {
        val reason =
            failureReasonFor(
                responseCode = 404,
                body = """{"error":{"code":404,"message":"The requested URL was not found.","status":"NOT_FOUND"}}""",
            )

        assertEquals("http_error_404: The requested URL was not found.", reason)
    }

    @Test
    fun `a rejected key is reported as invalid_api_key even though Google returns 400`() {
        val reason =
            failureReasonFor(
                responseCode = 400,
                body =
                    """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.",""" +
                        """"status":"API_KEY_INVALID"}}""",
            )

        assertTrue(reason, reason.startsWith("invalid_api_key: "))
    }

    @Test
    fun `a 403 is reported as auth_failed`() {
        val reason =
            failureReasonFor(
                responseCode = 403,
                body = """{"error":{"code":403,"message":"Permission denied.","status":"PERMISSION_DENIED"}}""",
            )

        assertEquals("auth_failed: Permission denied.", reason)
    }

    @Test
    fun `a 429 is reported as rate_limited`() {
        val reason =
            failureReasonFor(
                responseCode = 429,
                body = """{"error":{"code":429,"message":"Quota exceeded.","status":"RESOURCE_EXHAUSTED"}}""",
            )

        assertEquals("rate_limited: Quota exceeded.", reason)
    }

    /** `GeminiQueryParamAuthInterceptor` puts the key in the URL, and Google
     * quotes the offending URL back in some error paths — the reason string
     * reaches the Settings screen, so it must never carry the key. */
    @Test
    fun `an error message echoing the key back has it redacted before it reaches the caller`() {
        val reason =
            failureReasonFor(
                responseCode = 404,
                body =
                    """{"error":{"code":404,"message":"Not found: /v1beta/models/x:generateContent""" +
                        """?key=super-secret-key","status":"NOT_FOUND"}}""",
                apiKey = "super-secret-key",
            )

        assertFalse("the reason leaked the API key: $reason", reason.contains("super-secret-key"))
        assertTrue(reason, reason.contains("<redacted>"))
    }

    @Test
    fun `an error body that is not valid JSON still yields a labelled failure rather than throwing`() {
        val reason = failureReasonFor(responseCode = 404, body = "<html>404 Not Found</html>")

        assertEquals("http_error_404", reason)
    }

    @Test
    fun `a malformed success body is reported as a malformed response`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"candidates":"not-an-array"}"""))

            val result = adapter.run(request())

            check(result is VisionPromptAdapterResult.Failure)
            assertTrue(result.reason, result.reason.startsWith("malformed_response"))
        }
}
