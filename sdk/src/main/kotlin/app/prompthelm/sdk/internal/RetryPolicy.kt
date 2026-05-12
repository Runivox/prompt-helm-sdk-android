package app.prompthelm.sdk.internal

import app.prompthelm.sdk.AuthenticationException
import app.prompthelm.sdk.AuthorizationException
import app.prompthelm.sdk.NotFoundException
import app.prompthelm.sdk.PromptHelmException
import app.prompthelm.sdk.PromptHelmTimeoutException
import app.prompthelm.sdk.RateLimitException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Decides whether a thrown error should be retried.
 *
 * Retry policy mirrors the Node SDK exactly:
 *  - 5xx → retry
 *  - network / IO → retry
 *  - 401, 403, 404, 429 → DO NOT retry (caller error or rate limit)
 *  - timeout → DO NOT retry (caller widens budget)
 *  - cancellation → propagate immediately
 */
internal object RetryPolicy {

    private const val DEFAULT_BASE_DELAY_MS = 250L
    private const val DEFAULT_MAX_DELAY_MS = 8_000L

    fun isRetryable(throwable: Throwable): Boolean = when (throwable) {
        is CancellationException -> false
        is PromptHelmTimeoutException -> false
        is HttpRequestTimeoutException -> false
        is AuthenticationException -> false
        is AuthorizationException -> false
        is NotFoundException -> false
        is RateLimitException -> false
        is PromptHelmException -> throwable.statusCode in 500..599
        else -> true // network / IO / engine error → retry
    }

    fun computeBackoffMillis(
        attempt: Int,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        random: Random = Random.Default,
    ): Long {
        val exponential = baseDelayMs shl attempt // baseDelayMs * 2^attempt
        val jitter = random.nextLong(0, 100)
        return min(exponential + jitter, maxDelayMs)
    }

    suspend fun <T> withRetry(
        maxRetries: Int,
        sleep: suspend (Long) -> Unit = ::delay,
        random: Random = Random.Default,
        block: suspend () -> T,
    ): T {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (attempt >= maxRetries || !isRetryable(t)) throw t
                sleep(computeBackoffMillis(attempt, random = random))
                attempt += 1
            }
        }
    }
}
