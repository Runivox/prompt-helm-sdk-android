package app.prompthelm.sdk

import app.prompthelm.sdk.internal.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RetryTest {

    @Test
    fun `isRetryable returns true for 5xx`() {
        listOf(500, 502, 503, 504).forEach { status ->
            val ex = ApiException(status, null, null, "boom")
            assertTrue(RetryPolicy.isRetryable(ex), "status=$status should retry")
        }
    }

    @Test
    fun `isRetryable returns false for 4xx mapped subclasses`() {
        assertEquals(false, RetryPolicy.isRetryable(AuthenticationException(401, null, null, "x")))
        assertEquals(false, RetryPolicy.isRetryable(AuthorizationException(403, null, null, "x")))
        assertEquals(false, RetryPolicy.isRetryable(NotFoundException(404, null, null, "x")))
        assertEquals(false, RetryPolicy.isRetryable(RateLimitException(429, null, null, "x")))
    }

    @Test
    fun `isRetryable returns false for timeout`() {
        assertEquals(false, RetryPolicy.isRetryable(PromptHelmTimeoutException(1000)))
    }

    @Test
    fun `isRetryable returns true for arbitrary network errors`() {
        assertTrue(RetryPolicy.isRetryable(java.io.IOException("connection reset")))
    }

    @Test
    fun `computeBackoffMillis grows exponentially with bounded jitter`() {
        val a0 = RetryPolicy.computeBackoffMillis(0, random = Random(42))
        val a1 = RetryPolicy.computeBackoffMillis(1, random = Random(42))
        val a2 = RetryPolicy.computeBackoffMillis(2, random = Random(42))

        assertTrue(a0 in 250L..(250L + 100L), "attempt 0 was $a0")
        assertTrue(a1 in 500L..(500L + 100L), "attempt 1 was $a1")
        assertTrue(a2 in 1000L..(1000L + 100L), "attempt 2 was $a2")
    }

    @Test
    fun `computeBackoffMillis caps at maxDelayMs`() {
        val capped = RetryPolicy.computeBackoffMillis(
            attempt = 20,
            baseDelayMs = 250,
            maxDelayMs = 8000,
            random = Random(1),
        )
        assertEquals(8000L, capped)
    }

    @Test
    fun `withRetry retries until success`() = runTest {
        var attempts = 0
        val sleeps = mutableListOf<Long>()
        val result = RetryPolicy.withRetry(
            maxRetries = 3,
            sleep = { sleeps.add(it) },
        ) {
            attempts += 1
            if (attempts < 3) throw java.io.IOException("transient")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(2, sleeps.size)
    }

    @Test
    fun `withRetry stops at maxRetries and rethrows`() = runTest {
        var attempts = 0
        val ex = runCatching {
            RetryPolicy.withRetry(maxRetries = 2, sleep = {}) {
                attempts += 1
                throw java.io.IOException("nope")
            }
        }.exceptionOrNull()
        assertEquals(3, attempts) // initial + 2 retries
        assertTrue(ex is java.io.IOException)
    }

    @Test
    fun `withRetry does not retry non-retryable errors`() = runTest {
        var attempts = 0
        val ex = runCatching {
            RetryPolicy.withRetry(maxRetries = 5, sleep = {}) {
                attempts += 1
                throw AuthenticationException(401, null, null, "no")
            }
        }.exceptionOrNull()
        assertEquals(1, attempts)
        assertTrue(ex is AuthenticationException)
    }

    @Test
    fun `client retries 5xx end-to-end`() = runTest {
        val successBody = """
            {"id":"e1","output":"ok","model":"m","inputTokens":1,"outputTokens":1,"totalTokens":2,"latencyMs":1,"cost":0.0,"timestamp":"2026-05-13T10:00:00.000Z"}
        """.trimIndent()
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount += 1
            if (requestCount < 2) {
                respond(
                    content = """{"statusCode":500,"error":"Internal Server Error","message":"boom"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = successBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            maxRetries = 3,
            httpEngine = engine,
        )

        val response = client.execute(ExecuteRequest(promptSlug = "x"))
        assertEquals("e1", response.id)
        assertEquals(2, requestCount)
        client.close()
    }
}
