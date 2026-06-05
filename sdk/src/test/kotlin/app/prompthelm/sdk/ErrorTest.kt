package app.prompthelm.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorTest {

    @Test
    fun `ErrorMapper maps 401 to AuthenticationException`() {
        val mapped = ErrorMapper.fromHttp(
            status = 401,
            envelope = ErrorEnvelope(
                statusCode = 401,
                errorCode = "UNAUTHORIZED",
                message = "bad key",
                timestamp = "2026-06-05T10:30:00.000Z",
                requestId = "req-1",
            ),
        )
        assertTrue(mapped is AuthenticationException)
        assertEquals(401, mapped.statusCode)
        assertEquals("UNAUTHORIZED", mapped.errorCode)
        assertEquals("req-1", mapped.requestId)
        assertEquals("bad key", mapped.message)
    }

    @Test
    fun `ErrorMapper maps 403 to AuthorizationException`() {
        val mapped = ErrorMapper.fromHttp(403, null)
        assertTrue(mapped is AuthorizationException)
        assertNotNull(mapped.message)
    }

    @Test
    fun `ErrorMapper maps 404 to NotFoundException`() {
        val mapped = ErrorMapper.fromHttp(404, null)
        assertTrue(mapped is NotFoundException)
    }

    @Test
    fun `ErrorMapper maps 429 to RateLimitException`() {
        val mapped = ErrorMapper.fromHttp(429, null)
        assertTrue(mapped is RateLimitException)
    }

    @Test
    fun `ErrorMapper maps unknown statuses to ApiException`() {
        val mapped = ErrorMapper.fromHttp(503, null)
        assertTrue(mapped is ApiException)
        assertEquals(503, mapped.statusCode)
    }

    @Test
    fun `ErrorMapper produces a non-empty fallback message when envelope is null`() {
        listOf(401, 403, 404, 429, 500, 502, 504).forEach { status ->
            val mapped = ErrorMapper.fromHttp(status, null)
            assertTrue(
                !mapped.message.isNullOrBlank(),
                "fallback message must not be blank for status=$status",
            )
        }
    }

    @Test
    fun `PromptHelmTimeoutException carries the timeout value`() {
        val ex = PromptHelmTimeoutException(timeoutMillis = 1234)
        assertEquals(1234L, ex.timeoutMillis)
        assertTrue(ex.message!!.contains("1234"))
    }
}
