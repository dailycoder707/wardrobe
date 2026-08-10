package com.wardrobe.app.core.ai.job

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

const val KEY_JOB_ID = "ai_job_id"
const val KEY_TIMEOUT_MS = "ai_job_timeout_ms"
const val DEFAULT_TIMEOUT_MS = 30_000L

/** WorkManager retries a fixed number of times before giving up — this is
 * this project's own choice, not a WorkManager default (which retries
 * indefinitely otherwise). */
private const val MAX_RUN_ATTEMPTS = 3

/**
 * The generic, capability-agnostic worker every [AiJobManager] dispatch
 * runs through — same `@HiltWorker`/`@AssistedInject` shape as
 * `core:data`'s existing `ImageProcessingWorker`, picked up automatically by
 * `WardrobeApplication`'s already-configured `HiltWorkerFactory`. Looks its
 * actual request up from [AiWorkRegistry] by job id (see that class's KDoc
 * for why), runs it under [withTimeout] (the one piece WorkManager doesn't
 * provide natively), and retries on a network-level [IOException] or
 * timeout — `Result.retry()` triggers WorkManager's own exponential
 * backoff, configured by [AiJobManager] at enqueue time.
 *
 * This worker — not [AiJobManager.dispatch]'s caller — owns removing the
 * entry from [AiWorkRegistry] once the job truly reaches a terminal state
 * (success, or final failure after retries are exhausted). Removing it here
 * rather than in the caller's `finally` means a caller that gets cancelled
 * mid-await (e.g. its ViewModel scope closes) can't rip the in-flight
 * registry entry out from under another caller who joined the same
 * in-flight request via [AiWorkRegistry.registerIfAbsent].
 */
@HiltWorker
class AiCapabilityWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val registry: AiWorkRegistry,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val jobId = inputData.getString(KEY_JOB_ID)
            val pending = jobId?.let(registry::get)
            if (jobId == null || pending == null) return Result.failure()
            val timeoutMs = inputData.getLong(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
            return try {
                val outcome = withTimeout(timeoutMs) { pending.block() }
                pending.deferred.complete(outcome)
                registry.remove(jobId)
                Result.success()
            } catch (timeout: TimeoutCancellationException) {
                retryOrFail(jobId, pending, timeout)
            } catch (io: IOException) {
                retryOrFail(jobId, pending, io)
            }
        }

        private fun retryOrFail(
            jobId: String,
            pending: PendingAiWork,
            error: Throwable,
        ): Result {
            if (runAttemptCount < MAX_RUN_ATTEMPTS - 1) return Result.retry()
            pending.deferred.completeExceptionally(error)
            registry.remove(jobId)
            return Result.failure()
        }
    }
