package app.prompthelm.sdk.internal

import app.prompthelm.sdk.StreamEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Incremental Server-Sent Events parser.
 *
 * Tracks `data:` lines per frame and flushes a complete frame on the
 * first blank line. Comments (`:`-prefixed lines) and unknown fields
 * are skipped silently to match the SSE spec and the Node SDK.
 *
 * The parser is **not** thread-safe; create one per stream.
 */
internal class SseParser {

    private val dataLines = StringBuilder()
    private var hasData = false

    /**
     * Feed one already-split line (without trailing newline). Returns
     * a [StreamEvent] when the line completes a frame, otherwise `null`.
     *
     * Returns `null` for:
     *  - intra-frame `data:` lines (still buffering)
     *  - comments and unknown fields
     *  - frames whose payload is `[DONE]` (OpenAI sentinel)
     *  - JSON that does not match the discriminated union
     */
    fun feed(line: String): StreamEvent? {
        if (line.isEmpty()) {
            return flushIfReady()
        }
        if (line.startsWith(":")) return null // comment

        val colonIndex = line.indexOf(':')
        val field: String
        val value: String
        if (colonIndex == -1) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colonIndex)
            value = line.substring(colonIndex + 1).removePrefix(" ")
        }

        if (field == "data") {
            if (hasData) dataLines.append('\n')
            dataLines.append(value)
            hasData = true
        }
        return null
    }

    /**
     * Force-emit any buffered frame (called when the underlying stream
     * closes without a trailing blank line).
     */
    fun flush(): StreamEvent? = flushIfReady()

    private fun flushIfReady(): StreamEvent? {
        if (!hasData) return null
        val payload = dataLines.toString()
        dataLines.clear()
        hasData = false
        return parsePayload(payload)
    }

    private fun parsePayload(raw: String): StreamEvent? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return null
        return try {
            JSON.decodeFromString(StreamEvent.serializer(), trimmed)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal companion object {
        internal val JSON: Json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
            isLenient = true
        }
    }
}
