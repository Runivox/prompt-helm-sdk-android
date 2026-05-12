package app.prompthelm.sdk

import kotlinx.serialization.Serializable

/**
 * Response payload for `POST /api/v1/gateway/execute`.
 *
 * `cost` is reported in USD with provider-specific precision.
 * `latencyMs` is the server-measured end-to-end latency for the
 * upstream provider call (does not include network time to the SDK).
 */
@Serializable
public data class ExecuteResponse(
    val id: String,
    val output: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val latencyMs: Int,
    val cost: Double,
    val timestamp: String,
)
