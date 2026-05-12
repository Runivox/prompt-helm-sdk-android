package app.prompthelm.sdk

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamTest {

    @Test
    fun `stream emits chunk events then a done event`() = runTest {
        val body = buildString {
            append("data: {\"type\":\"chunk\",\"content\":\"Hello\"}\n\n")
            append("data: {\"type\":\"chunk\",\"content\":\", world\"}\n\n")
            append("data: {\"type\":\"done\",\"inputTokens\":3,\"outputTokens\":4,\"totalTokens\":7,\"cost\":0.0001,\"model\":\"openai/gpt-4o-mini\",\"latencyMs\":120}\n\n")
        }
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val events = client.stream(ExecuteRequest(promptSlug = "x")).toList()

        assertEquals(3, events.size)
        assertEquals("Hello", (events[0] as StreamEvent.Chunk).content)
        assertEquals(", world", (events[1] as StreamEvent.Chunk).content)
        val done = events[2] as StreamEvent.Done
        assertEquals(7, done.totalTokens)
        assertEquals("openai/gpt-4o-mini", done.model)
        client.close()
    }

    @Test
    fun `stream emits an Err event when the server sends one`() = runTest {
        val body = buildString {
            append("data: {\"type\":\"chunk\",\"content\":\"partial\"}\n\n")
            append("data: {\"type\":\"error\",\"errorCode\":\"provider_timeout\",\"message\":\"upstream slow\",\"requestId\":\"req-9\"}\n\n")
        }
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val events = client.stream(ExecuteRequest(promptSlug = "x")).toList()

        assertEquals(2, events.size)
        val err = events[1] as StreamEvent.Err
        assertEquals("provider_timeout", err.errorCode)
        assertEquals("upstream slow", err.message)
        assertEquals("req-9", err.requestId)
        client.close()
    }

    @Test
    fun `stream tolerates comment frames and unknown fields`() = runTest {
        val body = buildString {
            append(": this is a keep-alive comment\n")
            append("event: ignored-by-spec\n")
            append("data: {\"type\":\"chunk\",\"content\":\"ok\"}\n\n")
            append("data: {\"type\":\"done\",\"inputTokens\":1,\"outputTokens\":1,\"totalTokens\":2,\"cost\":0,\"model\":\"m\",\"latencyMs\":1}\n\n")
        }
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val events = client.stream(ExecuteRequest(promptSlug = "x")).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is StreamEvent.Chunk)
        assertTrue(events[1] is StreamEvent.Done)
        client.close()
    }

    @Test
    fun `stream supports multi-line data fields (joined with newline)`() = runTest {
        val body = buildString {
            append("data: {\"type\":\"chunk\",\n")
            append("data: \"content\":\"multi\"}\n\n")
            append("data: {\"type\":\"done\",\"inputTokens\":0,\"outputTokens\":0,\"totalTokens\":0,\"cost\":0,\"model\":\"m\",\"latencyMs\":0}\n\n")
        }
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val events = client.stream(ExecuteRequest(promptSlug = "x")).toList()

        assertTrue(events.any { it is StreamEvent.Chunk && it.content == "multi" })
        assertTrue(events.last() is StreamEvent.Done)
        client.close()
    }

    @Test
    fun `stream throws AuthenticationException when initial response is 401`() = runTest {
        val errorBody = """{"statusCode":401,"error":"Unauthorized","message":"bad key"}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.Unauthorized, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val ex = runCatching {
            client.stream(ExecuteRequest(promptSlug = "x")).toList()
        }.exceptionOrNull()
        assertTrue(ex is AuthenticationException, "expected AuthenticationException, got $ex")
        client.close()
    }

    @Test
    fun `stream hits the v1 stream path with text-event-stream accept`() = runTest {
        val body = "data: {\"type\":\"done\",\"inputTokens\":0,\"outputTokens\":0,\"totalTokens\":0,\"cost\":0,\"model\":\"m\",\"latencyMs\":0}\n\n"
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        client.stream(ExecuteRequest(promptSlug = "x")).toList()

        val sent = engine.requestHistory.first()
        assertEquals("/api/v1/gateway/stream", sent.url.encodedPath)
        val accept = sent.headers["Accept"]
        assertTrue(accept != null && accept.contains("text/event-stream"), "Accept=$accept")
        client.close()
    }

    @Test
    fun `stream ignores DONE sentinel frames`() = runTest {
        val body = buildString {
            append("data: {\"type\":\"chunk\",\"content\":\"x\"}\n\n")
            append("data: [DONE]\n\n")
            append("data: {\"type\":\"done\",\"inputTokens\":1,\"outputTokens\":1,\"totalTokens\":2,\"cost\":0,\"model\":\"m\",\"latencyMs\":1}\n\n")
        }
        val engine = MockHttpClient.engine(MockHttpClient.sseResponse(body))
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val events = client.stream(ExecuteRequest(promptSlug = "x")).toList()

        assertEquals(2, events.size)
        assertTrue(events.last() is StreamEvent.Done)
        client.close()
    }
}
