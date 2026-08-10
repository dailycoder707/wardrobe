package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BITMAP_SIZE = 4

@RunWith(RobolectricTestRunner::class)
class OpenRouterAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenRouterAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = OpenRouterAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run posts to v1 chat completions with a Bearer token, same as OpenAI`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"suggestion"}}]}"""))

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "router-key",
                        model = "openrouter/some-model",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                ) as VisionPromptAdapterResult.Success

            assertEquals("suggestion", result.rawResponseText)
            val recorded = server.takeRequest()
            assertEquals("/v1/chat/completions", recorded.path)
            assertEquals("Bearer router-key", recorded.getHeader("Authorization"))
        }
}
