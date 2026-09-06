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
class OllamaAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OllamaAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = OllamaAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run sends no Authorization header for a blank local API key`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"local-suggestion"}}]}"""))

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "",
                        model = "llava",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Success)
            assertEquals("local-suggestion", result.rawResponseText)
            val recorded = server.takeRequest()
            assertEquals("/v1/chat/completions", recorded.path)
            assertNull(recorded.getHeader("Authorization"))
        }
}
