package app.prompthelm.sdk

import app.prompthelm.sdk.internal.HttpClient
import app.prompthelm.sdk.internal.RetryPolicy
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for the PromptHelm Android SDK.
 *
 * ```kotlin
 * val client = PromptHelm(apiKey = BuildConfig.PROMPT_HELM_KEY)
 * val response = client.execute(
 *     ExecuteRequest(
 *         promptSlug = "support-triage",
 *         variables = mapOf("ticket" to ticketBody),
 *     ),
 * )
 * ```
 *
 * The client is **safe to share** across coroutines and screens —
 * Ktor's underlying engine pools connections. Create one instance per
 * process (e.g. as a singleton in your DI graph) and call [close] when
 * the host process is shutting down.
 *
 * The constructor validates the API key shape eagerly and throws
 * [IllegalArgumentException] for malformed input. All other failures
 * surface as [PromptHelmException] subclasses on the suspending APIs.
 */
public class PromptHelm @JvmOverloads public constructor(
    apiKey: String,
    baseUrl: String = PromptHelmConfig.DEFAULT_BASE_URL,
    timeoutMillis: Long = PromptHelmConfig.DEFAULT_TIMEOUT_MILLIS,
    maxRetries: Int = PromptHelmConfig.DEFAULT_MAX_RETRIES,
    userAgent: String? = null,
    httpEngine: HttpClientEngine? = null,
) : AutoCloseable {

    public constructor(config: PromptHelmConfig) : this(
        apiKey = config.apiKey,
        baseUrl = config.baseUrl,
        timeoutMillis = config.timeoutMillis,
        maxRetries = config.maxRetries,
        userAgent = config.userAgent,
        httpEngine = config.httpEngine,
    )

    private val maxRetries: Int

    private val http: HttpClient

    init {
        require(apiKey.isNotEmpty()) {
            "PromptHelm: `apiKey` is required. Provide a key starting with `phk_`."
        }
        require(apiKey.startsWith(API_KEY_PREFIX) && apiKey.length == API_KEY_LENGTH) {
            "PromptHelm: `apiKey` must start with `phk_` followed by 32 hex characters."
        }
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "PromptHelm: `baseUrl` must be an absolute http(s) URL — got `$baseUrl`."
        }
        require(timeoutMillis > 0) {
            "PromptHelm: `timeoutMillis` must be a positive number."
        }
        require(maxRetries >= 0) {
            "PromptHelm: `maxRetries` must be a non-negative integer."
        }

        this.maxRetries = maxRetries
        this.http = HttpClient(
            config = PromptHelmConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                timeoutMillis = timeoutMillis,
                maxRetries = maxRetries,
                userAgent = userAgent,
                httpEngine = httpEngine,
            ),
            sdkVersion = SDK_VERSION,
        )
    }

    /**
     * Run a prompt synchronously through the gateway and return the
     * full response.
     *
     * Retries on 5xx and network errors with exponential back-off
     * (250 ms, 500 ms, 1 s, …) up to [PromptHelmConfig.maxRetries]
     * additional attempts. Does **not** retry 4xx, timeouts, or
     * cancellations.
     *
     * @throws AuthenticationException 401
     * @throws AuthorizationException  403
     * @throws NotFoundException       404
     * @throws RateLimitException      429
     * @throws ApiException            other 4xx / 5xx
     * @throws PromptHelmTimeoutException request exceeded the configured timeout
     */
    public suspend fun execute(request: ExecuteRequest): ExecuteResponse =
        RetryPolicy.withRetry(maxRetries) { http.execute(request) }

    /**
     * Stream a prompt response as a cold [Flow] of [StreamEvent]s.
     *
     * Streaming requests are **not** retried — once a chunk has been
     * delivered to the consumer the response is committed.
     *
     * Cancel the collecting coroutine to abort the stream early; the
     * SDK will release the underlying HTTP connection.
     *
     * The flow completes after a [StreamEvent.Done] event. A
     * [StreamEvent.Err] event triggers an [ApiException] downstream.
     */
    public fun stream(request: ExecuteRequest): Flow<StreamEvent> =
        http.stream(request)

    override fun close() {
        http.close()
    }

    public companion object {
        public const val SDK_VERSION: String = "0.2.0"
        internal const val API_KEY_PREFIX = "phk_"
        internal const val API_KEY_LENGTH = 36
    }
}
