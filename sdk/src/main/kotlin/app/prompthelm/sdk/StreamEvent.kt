package app.prompthelm.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Discriminated union of server-sent events emitted on
 * `POST /api/v1/gateway/stream`.
 *
 * The wire format is `{ "type": "chunk" | "done" | "error", ... }`,
 * matching every other PromptHelm SDK.
 *
 * Note: [Err] is the public name for the `"error"` variant. We avoid
 * `Error` to prevent clashing with `kotlin.Error` at call sites.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
public sealed class StreamEvent {

    @Serializable
    @SerialName("chunk")
    public data class Chunk(
        val content: String,
    ) : StreamEvent()

    @Serializable
    @SerialName("done")
    public data class Done(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val cost: Double,
        val model: String,
        val latencyMs: Int,
    ) : StreamEvent()

    @Serializable
    @SerialName("error")
    public data class Err(
        val errorCode: String,
        val message: String,
        val requestId: String? = null,
    ) : StreamEvent()
}
