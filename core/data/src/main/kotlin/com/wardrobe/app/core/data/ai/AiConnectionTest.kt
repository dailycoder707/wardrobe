package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import java.time.Clock
import kotlin.random.Random

/** [AiProviderSettingsRepositoryImpl.testConnection]'s two dispatch shapes,
 * split out (Detekt's `TooManyFunctions`) as top-level functions taking the
 * gateway/clock they need as parameters rather than as private members. */

internal const val TEST_CONNECTION_PROMPT_VERSION = "test-connection-v1"
internal const val TEST_CONNECTION_TASK_TYPE = "ping"
internal const val TEST_CONNECTION_SYSTEM_PROMPT = "Respond with the single word OK."
internal const val TEST_CONNECTION_USER_PROMPT = "OK?"

internal suspend fun testVisionPromptConnection(
    aiGateway: AiGateway,
    context: AiDispatchContext,
    clock: Clock,
    startedAt: Long,
): AiConnectionTestResult {
    val result =
        aiGateway.runVisionPrompt(
            context,
            TEST_CONNECTION_PROMPT_VERSION,
            TEST_CONNECTION_SYSTEM_PROMPT,
            TEST_CONNECTION_USER_PROMPT,
            randomPingBitmap(),
        )
    return when (result) {
        is VisionPromptResult.Success -> AiConnectionTestResult.Success(clock.millis() - startedAt)
        is VisionPromptResult.Failure -> AiConnectionTestResult.Failure(result.reason)
    }
}

internal suspend fun testImageTaskConnection(
    aiGateway: AiGateway,
    context: AiDispatchContext,
    clock: Clock,
    startedAt: Long,
): AiConnectionTestResult {
    val result =
        aiGateway.runImageTask(
            context,
            TEST_CONNECTION_PROMPT_VERSION,
            TEST_CONNECTION_TASK_TYPE,
            listOf(randomPingBitmap()),
        )
    return when (result) {
        is ImageTaskResult.Success -> AiConnectionTestResult.Success(clock.millis() - startedAt)
        is ImageTaskResult.Failure -> AiConnectionTestResult.Failure(result.reason)
    }
}

/** A fresh, randomly-colored 1x1 pixel every call — the Gateway's cache is
 * keyed by payload hash, so a fixed test image would only ever exercise the
 * network on the very first tap and silently return a stale cached result
 * on every one after that. */
private fun randomPingBitmap(): Bitmap =
    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, Random.nextInt())
    }
