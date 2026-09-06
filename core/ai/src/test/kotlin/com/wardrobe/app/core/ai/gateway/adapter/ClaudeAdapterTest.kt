package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BITMAP_SIZE = 4

@RunWith(RobolectricTestRunner::class)
class ClaudeAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ClaudeAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ClaudeAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run posts to v1 messages with x-api-key and anthropic-version headers, not Bearer`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"content":[{"type":"text","text":"claude-suggestion"}],""" +
                        """"usage":{"input_tokens":20,"output_tokens":6}}""",
                ),
            )

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "claude-key",
                        model = "claude-3-5-sonnet-20241022",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Success)
            assertEquals("claude-suggestion", result.rawResponseText)
            assertEquals(20, result.estimatedInputTokens)
            assertEquals(6, result.estimatedOutputTokens)
            val recorded = server.takeRequest()
            assertEquals("/v1/messages", recorded.path)
            assertEquals("claude-key", recorded.getHeader("x-api-key"))
            assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
            assertNull(recorded.getHeader("Authorization"))
        }

    @Test
    fun `run returns Failure rather than fabricating a result when no text block is present`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"content":[]}"""))

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "claude-key",
                        model = null,
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Failure)
            assertEquals("no_content_in_response", result.reason)
        }
}
