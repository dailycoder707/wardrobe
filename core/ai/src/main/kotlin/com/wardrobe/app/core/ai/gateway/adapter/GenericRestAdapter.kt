package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapter
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapterRequest
import com.wardrobe.app.core.ai.gateway.ImageTaskAdapterResult
import com.wardrobe.app.core.image.pipeline.ImageResizer
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MULTIPART_IMAGE_QUALITY = 90

/**
 * The one [ImageTaskAdapter] implementation — [GenericRestService]'s
 * documented multipart contract, for any self-hosted or third-party
 * backend the user points at for Extraction/Reconstruction/Try-On. Never a
 * named vendor's actual API, since none of them share a standard shape for
 * this kind of task.
 */
@Singleton
class GenericRestAdapter
    @Inject
    constructor(
        private val service: GenericRestService,
    ) : ImageTaskAdapter {
        override suspend fun run(request: ImageTaskAdapterRequest): ImageTaskAdapterResult {
            val url = "${request.baseUrl.trimEnd('/')}/${request.taskType}"
            val headers =
                if (request.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${request.apiKey}")
            val parts = request.images.mapIndexed { index, bitmap -> toMultipart(bitmap, index) }
            return try {
                toAdapterResult(service.runImageTask(url, headers, parts))
            } catch (error: HttpException) {
                ImageTaskAdapterResult.Failure("http_error_${error.code()}")
            } catch (error: SerializationException) {
                ImageTaskAdapterResult.Failure("malformed_response: ${error.message}")
            }
        }

        private fun toMultipart(
            bitmap: Bitmap,
            index: Int,
        ): MultipartBody.Part {
            val output = ByteArrayOutputStream()
            ImageResizer.encodeWebpLossy(bitmap, MULTIPART_IMAGE_QUALITY, output)
            val body = output.toByteArray().toRequestBody("image/webp".toMediaType())
            return MultipartBody.Part.createFormData("images", "image_$index.webp", body)
        }

        private fun toAdapterResult(response: GenericImageTaskResponse): ImageTaskAdapterResult {
            val bitmap = response.resultImageBase64?.let(::decodeBase64Bitmap)
            return if (bitmap != null) {
                ImageTaskAdapterResult.Success(bitmap, response.confidence)
            } else {
                ImageTaskAdapterResult.Failure(response.error ?: "no_result_image_or_undecodable")
            }
        }

        /** A self-hosted backend's response is untrusted input — `Base64.decode`
         * throws [IllegalArgumentException] on malformed/truncated input
         * (invalid padding, for instance), which is neither an [HttpException]
         * nor a [SerializationException] and would otherwise propagate
         * uncaught out of the [AiJobManager][com.wardrobe.app.core.ai.job.AiJobManager]
         * worker that runs this adapter — leaving the caller's
         * `CompletableDeferred` permanently uncompleted (a hang, not a
         * failure) rather than a graceful [ImageTaskAdapterResult.Failure]. */
        private fun decodeBase64Bitmap(base64: String): Bitmap? =
            runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
    }
