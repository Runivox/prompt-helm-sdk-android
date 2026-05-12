package app.prompthelm.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Environment branch to dispatch the prompt against.
 *
 * Lower-case values mirror the backend `Environment` enum so the
 * over-the-wire JSON shape matches every other PromptHelm SDK.
 */
@Suppress("EnumEntryName")
@Serializable
public enum class Environment {
    @SerialName("production")
    production,

    @SerialName("development")
    development,
}

/**
 * Request payload for `POST /api/v1/gateway/execute` and
 * `POST /api/v1/gateway/stream`.
 *
 * Either reference a stored prompt via [promptSlug] / [promptId] **or**
 * supply ad-hoc [system] / [user] text. Mixing both is allowed:
 * inline text overrides the stored template at the corresponding role.
 *
 * All fields are optional on the server — pass only what you need.
 */
@Serializable
public data class ExecuteRequest(
    val promptSlug: String? = null,
    val promptId: String? = null,
    val variables: Map<String, String>? = null,
    val system: String? = null,
    val user: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val stopSequences: List<String>? = null,
    val environment: Environment? = null,
    val timeoutMs: Long? = null,
)
