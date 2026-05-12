package app.prompthelm.sdk

import io.ktor.client.engine.HttpClientEngine

/**
 * Optional configuration for the [PromptHelm] client.
 *
 * Construct with `PromptHelmConfig(apiKey = "phk_...")` and override
 * only the fields you care about. All non-required fields have safe
 * production defaults.
 *
 * @property apiKey        Required. Your PromptHelm API key. Must start with
 *                         `phk_` and be exactly 36 characters long.
 * @property baseUrl       Defaults to `https://api.prompthelm.app`.
 *                         Override only for staging or air-gapped deployments.
 * @property timeoutMillis Per-request timeout in milliseconds. Default `60_000`.
 *                         Applied to both `execute` and to the **initial**
 *                         response for `stream` (the streaming body itself
 *                         can run longer).
 * @property maxRetries    Number of additional attempts after the first
 *                         failure for retryable errors (5xx, network).
 *                         Default `2` (so up to 3 total attempts).
 * @property userAgent     Optional prefix prepended to the SDK's UA header.
 *                         Use this to identify your app: `"checkout/1.4.2"`.
 * @property httpEngine    Advanced. Inject a custom Ktor engine — e.g. a
 *                         pre-configured OkHttp engine with certificate
 *                         pinning. If `null` the SDK creates an OkHttp
 *                         engine internally.
 */
public data class PromptHelmConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val userAgent: String? = null,
    val httpEngine: HttpClientEngine? = null,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.prompthelm.app"
        public const val DEFAULT_TIMEOUT_MILLIS: Long = 60_000L
        public const val DEFAULT_MAX_RETRIES: Int = 2
    }
}
