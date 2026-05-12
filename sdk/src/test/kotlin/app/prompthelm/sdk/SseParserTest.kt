package app.prompthelm.sdk

import app.prompthelm.sdk.internal.SseParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SseParserTest {

    @Test
    fun `feed buffers data lines until a blank line`() {
        val parser = SseParser()
        assertNull(parser.feed("data: {\"type\":\"chunk\","))
        assertNull(parser.feed("data: \"content\":\"hi\"}"))
        val event = parser.feed("")
        assertNotNull(event)
        val chunk = event as StreamEvent.Chunk
        assertEquals("hi", chunk.content)
    }

    @Test
    fun `feed skips comment lines`() {
        val parser = SseParser()
        assertNull(parser.feed(": ping"))
        assertNull(parser.feed("data: {\"type\":\"chunk\",\"content\":\"a\"}"))
        val event = parser.feed("")
        assertNotNull(event)
    }

    @Test
    fun `feed returns null for malformed JSON payload`() {
        val parser = SseParser()
        assertNull(parser.feed("data: not-json"))
        val event = parser.feed("")
        assertNull(event)
    }

    @Test
    fun `feed returns null for DONE sentinel frame`() {
        val parser = SseParser()
        assertNull(parser.feed("data: [DONE]"))
        assertNull(parser.feed(""))
    }

    @Test
    fun `feed parses a done event with all required fields`() {
        val parser = SseParser()
        parser.feed("data: {\"type\":\"done\",\"inputTokens\":3,\"outputTokens\":4,\"totalTokens\":7,\"cost\":0.0001,\"model\":\"openai/gpt-4o\",\"latencyMs\":120}")
        val event = parser.feed("") as StreamEvent.Done
        assertEquals(3, event.inputTokens)
        assertEquals(4, event.outputTokens)
        assertEquals(7, event.totalTokens)
        assertEquals(0.0001, event.cost, 1e-9)
        assertEquals("openai/gpt-4o", event.model)
        assertEquals(120, event.latencyMs)
    }

    @Test
    fun `feed parses an error event with optional requestId`() {
        val parser = SseParser()
        parser.feed("data: {\"type\":\"error\",\"errorCode\":\"provider_timeout\",\"message\":\"slow\"}")
        val event = parser.feed("") as StreamEvent.Err
        assertEquals("provider_timeout", event.errorCode)
        assertEquals("slow", event.message)
        assertNull(event.requestId)
    }

    @Test
    fun `flush emits a buffered frame when stream ends without trailing blank line`() {
        val parser = SseParser()
        parser.feed("data: {\"type\":\"chunk\",\"content\":\"tail\"}")
        val event = parser.flush()
        assertTrue(event is StreamEvent.Chunk)
        assertEquals("tail", (event as StreamEvent.Chunk).content)
    }

    @Test
    fun `feed handles data without leading space after colon`() {
        val parser = SseParser()
        parser.feed("data:{\"type\":\"chunk\",\"content\":\"compact\"}")
        val event = parser.feed("") as StreamEvent.Chunk
        assertEquals("compact", event.content)
    }
}
