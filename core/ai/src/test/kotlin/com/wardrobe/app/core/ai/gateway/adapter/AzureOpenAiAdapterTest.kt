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
class AzureOpenAiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: AzureOpenAiAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = AzureOpenAiAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run puts the deployment name in the URL path and uses an api-key header, not Bearer`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "azure-key",
                        model = "my-deployment",
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Success)
            val recorded = server.takeRequest()
            assertEquals(true, recorded.path?.startsWith("/openai/deployments/my-deployment/chat/completions"))
            assertEquals("azure-key", recorded.getHeader("api-key"))
            assertNull(recorded.getHeader("Authorization"))
        }

    @Test
    fun `run fails clearly when no deployment name is configured`() =
        runTest {
            val result =
                adapter.run(
                    VisionPromptAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "azure-key",
                        model = null,
                        systemPrompt = "system",
                        userPrompt = "user",
                        image = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888),
                    ),
                )

            check(result is VisionPromptAdapterResult.Failure)
            assertEquals("azure_openai_requires_a_deployment_name_in_model", result.reason)
        }
}
