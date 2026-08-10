package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
class OpenAiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = OpenAiAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run sends the documented request shape and parses a successful response`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"content":"suggested-json"}}],"usage":{"prompt_tokens":12,"completion_tokens":4}}""",
                ),
            )

            val result = adapter.run(request()) as VisionPromptAdapterResult.Success

            assertEquals("suggested-json", result.rawResponseText)
            assertEquals(12, result.estimatedInputTokens)
            assertEquals(4, result.estimatedOutputTokens)

            val recorded = server.takeRequest()
            assertEquals("/v1/chat/completions", recorded.path)
            assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            val messages = body["messages"]!!.jsonArray
            assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        }

    @Test
    fun `run requests json_object response format only when the caller expects structured JSON`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))

            adapter.run(request(expectJsonResponse = true))
            val jsonRequestedBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            adapter.run(request(expectJsonResponse = false))
            val promptOnlyBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

            assertEquals(
                "json_object",
                jsonRequestedBody["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content,
            )
            assertTrue(
                "response_format must be absent, not just false, when not requested",
                "response_format" !in promptOnlyBody,
            )
        }

    @Test
    fun `run returns Failure rather than fabricating a result when the response has no content`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"choices":[]}"""))

            val result = adapter.run(request())

            assertTrue(result is VisionPromptAdapterResult.Failure)
            assertEquals("no_content_in_response", (result as VisionPromptAdapterResult.Failure).reason)
        }

    @Test
    fun `run maps an HTTP error response to a Failure carrying the status code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

            val result = adapter.run(request())

            assertTrue(result is VisionPromptAdapterResult.Failure)
            assertEquals("http_error_401", (result as VisionPromptAdapterResult.Failure).reason)
        }

    private fun request(expectJsonResponse: Boolean = false): VisionPromptAdapterRequest =
        VisionPromptAdapterRequest(
            baseUrl = server.url("/").toString(),
            apiKey = "test-key",
            model = "gpt-vision",
            systemPrompt = "system prompt",
            userPrompt = "user prompt",
            image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
            expectJsonResponse = expectJsonResponse,
        )
}
