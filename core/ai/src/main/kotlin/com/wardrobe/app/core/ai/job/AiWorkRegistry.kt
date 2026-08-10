package com.wardrobe.app.core.ai.job

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge between [AiJobManager]'s typed `dispatch<T>` call and
 * [AiCapabilityWorker]'s untyped `doWork()` — WorkManager can only pass
 * primitive `Data` between the two, so the actual request (a `Bitmap`,
 * prompt strings) and its result channel live here in memory, keyed by a
 * per-dispatch job id, rather than being serialized into `Data` at all.
 *
 * Honest limitation: this means a dispatched job cannot actually survive
 * process death — if the process dies, the in-memory request/result is
 * gone regardless of WorkManager's own on-disk persistence. What
 * WorkManager's retry/backoff/network-constraint machinery buys here is
 * real for the common case (the process stays alive across a transient
 * network blip or a brief backgrounding), not a full crash-recovery
 * guarantee — the same category of disclosed gap as the image pipeline's
 * own resume contract.
 *
 * Keyed by the Gateway's own cache key (not a random id): [registerIfAbsent]
 * is what lets two concurrent [AiJobManager.dispatch] calls for the
 * identical cache key (e.g. a double-tapped "Get Outfit Ideas" button)
 * coalesce into the single in-flight request instead of each firing its own
 * duplicate cloud call — see [AiJobManager]'s KDoc for why this matters.
 */
@Singleton
class AiWorkRegistry
    @Inject
    constructor() {
        private val pending = ConcurrentHashMap<String, PendingAiWork>()

        /** Atomically inserts [work] under [workKey] only if nothing is
         * already in flight for it, and returns whichever entry now owns
         * that key — the caller is the owner iff the returned reference is
         * `work` itself (compare with `===`); otherwise another in-flight
         * call already owns it and this one should just await that entry's
         * result instead of dispatching a second time. */
        fun registerIfAbsent(
            workKey: String,
            work: PendingAiWork,
        ): PendingAiWork = pending.putIfAbsent(workKey, work) ?: work

        fun get(workKey: String): PendingAiWork? = pending[workKey]

        fun remove(workKey: String) {
            pending.remove(workKey)
        }
    }

class PendingAiWork(
    val block: suspend () -> Any?,
    val deferred: CompletableDeferred<Any?> = CompletableDeferred(),
)
