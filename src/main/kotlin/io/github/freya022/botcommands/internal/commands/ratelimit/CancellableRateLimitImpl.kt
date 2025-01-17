package io.github.freya022.botcommands.internal.commands.ratelimit

import io.github.bucket4j.Bucket
import io.github.freya022.botcommands.api.commands.ratelimit.CancellableRateLimit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class CancellableRateLimitImpl internal constructor(private val bucket: Bucket) : CancellableRateLimit {
    private val lock = ReentrantLock()
    override var isRateLimitCancelled: Boolean = false

    override fun cancelRateLimit() = lock.withLock {
        if (!isRateLimitCancelled) {
            isRateLimitCancelled = true
            bucket.addTokens(1)
        }
    }
}