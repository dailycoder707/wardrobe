package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import android.util.Base64
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapterRequest
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapterResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

private const val BITMAP_SIZE = 4

@RunWith(RobolectricTestRunner::class)
class GenericRestAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GenericRestAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GenericRestAdapter(server.retrofitService())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `run posts a multipart request to baseUrl slash taskType and decodes the returned image`() =
        runTest {
            val resultBitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
            val resultBytes =
                ByteArrayOutputStream().apply {
                    resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                }
            val resultBase64 = Base64.encodeToString(resultBytes.toByteArray(), Base64.NO_WRAP)
            server.enqueue(MockResponse().setBody("""{"resultImageBase64":"$resultBase64","confidence":0.87}"""))

            val result =
                adapter.run(
                    ImageTaskAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "rest-key",
                        model = null,
                        taskType = "extract",
                        images = listOf(Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)),
                    ),
                )

            check(result is ImageTaskAdapterResult.Success)
            assertEquals(0.87f, result.confidence)
            assertEquals(BITMAP_SIZE, result.resultImage.width)
            val recorded = server.takeRequest()
            assertEquals("/extract", recorded.path)
            assertEquals("Bearer rest-key", recorded.getHeader("Authorization"))
            assertTrue(recorded.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
        }

    @Test
    fun `run returns Failure rather than fabricating a result when no image is returned`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"error":"could_not_process"}"""))

            val result =
                adapter.run(
                    ImageTaskAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "",
                        model = null,
                        taskType = "reconstruct",
                        images = listOf(Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)),
                    ),
                )

            check(result is ImageTaskAdapterResult.Failure)
            assertEquals("could_not_process", result.reason)
        }

    @Test
    fun `run returns Failure rather than hanging when resultImageBase64 is not valid base64`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"resultImageBase64":"A","confidence":0.9}"""))

            val result =
                adapter.run(
                    ImageTaskAdapterRequest(
                        baseUrl = server.url("/").toString(),
                        apiKey = "",
                        model = null,
                        taskType = "extract",
                        images = listOf(Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)),
                    ),
                )

            check(result is ImageTaskAdapterResult.Failure)
        }
}
