package app.prompthelm.sdk

import kotlinx.serialization.Serializable

/**
 * Wire shape of the backend's `GlobalExceptionFilter` response body.
 *
 * Mirrors the `{ statusCode, error, message, code?, correlationId? }`
 * contract documented in `prompt-helm-backend/docs/ERROR_CODES.md`.
 */
@Serializable
internal data class ErrorEnvelope(
    val statusCode: Int,
    val error: String,
    val message: String,
    val code: String? = null,
    val correlationId: String? = null,
)

/**
 * Base class for every error raised by the PromptHelm SDK after
 * receiving a structured error response from the API.
 *
 * @property statusCode    HTTP status code returned by the gateway.
 * @property code          Stable application-level error code (e.g. `provider_timeout`).
 *                         May be `null` when the gateway did not provide one.
 * @property correlationId The `x-correlation-id` echoed by the backend, useful
 *                         for log search. May be `null` if the response did not
 *                         carry one.
 */
public open class PromptHelmException(
    public val statusCode: Int,
    public val code: String?,
    public val correlationId: String?,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** HTTP 401 — invalid or revoked API key. NOT retryable. */
public class AuthenticationException(
    statusCode: Int,
    code: String?,
    correlationId: String?,
    message: String,
) : PromptHelmException(statusCode, code, correlationId, message)

/** HTTP 403 — authenticated but lacks permission. NOT retryable. */
public class AuthorizationException(
    statusCode: Int,
    code: String?,
    correlationId: String?,
    message: String,
) : PromptHelmException(statusCode, code, correlationId, message)

/** HTTP 404 — prompt slug / id does not resolve in this environment. NOT retryable. */
public class NotFoundException(
    statusCode: Int,
    code: String?,
    correlationId: String?,
    message: String,
) : PromptHelmException(statusCode, code, correlationId, message)

/** HTTP 429 — rate limit exceeded. NOT retried automatically; honour `Retry-After`. */
public class RateLimitException(
    statusCode: Int,
    code: String?,
    correlationId: String?,
    message: String,
) : PromptHelmException(statusCode, code, correlationId, message)

/** Any other 4xx/5xx failure. 5xx are retried; 4xx are not. */
public class ApiException(
    statusCode: Int,
    code: String?,
    correlationId: String?,
    message: String,
    cause: Throwable? = null,
) : PromptHelmException(statusCode, code, correlationId, message, cause)

/**
 * The request exceeded the configured per-call timeout.
 *
 * NOT retried automatically — the caller decides whether to widen
 * the budget or surface the failure to the user.
 */
public class PromptHelmTimeoutException(
    public val timeoutMillis: Long,
    message: String = "Request timed out after ${timeoutMillis}ms",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal object ErrorMapper {

    fun fromHttp(status: Int, envelope: ErrorEnvelope?): PromptHelmException {
        val message = envelope?.message ?: fallbackMessage(status)
        val code = envelope?.code
        val correlationId = envelope?.correlationId

        return when (status) {
            401 -> AuthenticationException(status, code, correlationId, message)
            403 -> AuthorizationException(status, code, correlationId, message)
            404 -> NotFoundException(status, code, correlationId, message)
            429 -> RateLimitException(status, code, correlationId, message)
            else -> ApiException(status, code, correlationId, message)
        }
    }

    private fun fallbackMessage(status: Int): String = when {
        status == 401 -> "Authentication failed. Check that your API key is valid and not revoked."
        status == 403 -> "You do not have permission to perform this action."
        status == 404 -> "The requested prompt or resource was not found."
        status == 429 -> "Rate limit exceeded. Slow down requests or upgrade your plan."
        status >= 500 -> "PromptHelm encountered an internal error. The request can be retried."
        else -> "Request failed with status $status."
    }
}
