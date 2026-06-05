package app.prompthelm.sdk

import kotlinx.serialization.Serializable

/**
 * Wire shape of the backend's `GlobalExceptionFilter` response body.
 *
 * Mirrors the exact envelope emitted by the PromptHelm gateway:
 * `{ statusCode, errorCode, message, timestamp, requestId }`. All five
 * fields are always present on a real error response; the SDK tolerates
 * missing fields defensively so a malformed body never masks the
 * underlying HTTP status.
 */
@Serializable
internal data class ErrorEnvelope(
    val statusCode: Int? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val timestamp: String? = null,
    val requestId: String? = null,
)

/**
 * Base class for every error raised by the PromptHelm SDK after
 * receiving a structured error response from the API.
 *
 * @property statusCode HTTP status code returned by the gateway.
 * @property errorCode  Machine-readable application error code (e.g.
 *                      `VALIDATION_ERROR`, `GATEWAY_MISSING_VARIABLES`).
 *                      May be `null` when the gateway did not provide one.
 * @property requestId  The `requestId` echoed by the backend (from the
 *                      `x-request-id` header or a generated UUID), useful
 *                      for log search. May be `null` if the response did
 *                      not carry one.
 */
public open class PromptHelmException(
    public val statusCode: Int,
    public val errorCode: String?,
    public val requestId: String?,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** HTTP 401 — invalid or revoked API key. NOT retryable. */
public class AuthenticationException(
    statusCode: Int,
    errorCode: String?,
    requestId: String?,
    message: String,
) : PromptHelmException(statusCode, errorCode, requestId, message)

/** HTTP 403 — authenticated but lacks permission / scope. NOT retryable. */
public class AuthorizationException(
    statusCode: Int,
    errorCode: String?,
    requestId: String?,
    message: String,
) : PromptHelmException(statusCode, errorCode, requestId, message)

/** HTTP 404 — prompt slug / id does not resolve in this environment. NOT retryable. */
public class NotFoundException(
    statusCode: Int,
    errorCode: String?,
    requestId: String?,
    message: String,
) : PromptHelmException(statusCode, errorCode, requestId, message)

/** HTTP 429 — rate limit exceeded. NOT retried automatically; honour `Retry-After`. */
public class RateLimitException(
    statusCode: Int,
    errorCode: String?,
    requestId: String?,
    message: String,
) : PromptHelmException(statusCode, errorCode, requestId, message)

/** Any other 4xx/5xx failure. 5xx are retried; 4xx are not. */
public class ApiException(
    statusCode: Int,
    errorCode: String?,
    requestId: String?,
    message: String,
    cause: Throwable? = null,
) : PromptHelmException(statusCode, errorCode, requestId, message, cause)

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
        val errorCode = envelope?.errorCode
        val requestId = envelope?.requestId

        return when (status) {
            401 -> AuthenticationException(status, errorCode, requestId, message)
            403 -> AuthorizationException(status, errorCode, requestId, message)
            404 -> NotFoundException(status, errorCode, requestId, message)
            429 -> RateLimitException(status, errorCode, requestId, message)
            else -> ApiException(status, errorCode, requestId, message)
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
