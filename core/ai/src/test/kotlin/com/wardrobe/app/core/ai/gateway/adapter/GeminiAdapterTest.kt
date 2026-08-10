package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
}
