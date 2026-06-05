package app.prompthelm.sdk

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecuteTest {

    private val successBody = """
        {
          "id": "exec_01HX",
          "output": "Hello, world.",
          "model": "openai/gpt-4o-mini",
          "inputTokens": 12,
          "outputTokens": 5,
          "totalTokens": 17,
          "latencyMs": 240,
          "cost": 0.000123,
          "timestamp": "2026-05-13T10:00:00.000Z"
        }
    """.trimIndent()

    @Test
    fun `execute returns a parsed ExecuteResponse on 200`() = runTest {
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.OK, successBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val response = client.execute(ExecuteRequest(promptSlug = "support-triage"))

        assertEquals("exec_01HX", response.id)
        assertEquals("Hello, world.", response.output)
        assertEquals(17, response.totalTokens)
        assertEquals(240, response.latencyMs)
        assertEquals(0.000123, response.cost, 1e-9)
        client.close()
    }

    @Test
    fun `execute sends Authorization, Accept, Content-Type and User-Agent headers`() = runTest {
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.OK, successBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            userAgent = "checkout-svc/1.4.2",
            httpEngine = engine,
        )

        client.execute(ExecuteRequest(promptSlug = "x"))

        val sent = engine.requestHistory.first()
        assertEquals("Bearer ${MockHttpClient.TEST_API_KEY}", sent.headers["Authorization"])
        val accept = sent.headers["Accept"]
        assertNotNull(accept)
        assertTrue(accept!!.contains("application/json"))
        val contentType = sent.headers["Content-Type"] ?: sent.body.contentType?.toString()
        assertNotNull(contentType)
        assertTrue(contentType!!.contains("application/json"))
        val ua = sent.headers["User-Agent"]
        assertNotNull(ua)
        assertTrue(ua!!.startsWith("checkout-svc/1.4.2 prompt-helm-sdk-android/"))
        client.close()
    }

    @Test
    fun `execute hits the v1 gateway path`() = runTest {
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.OK, successBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        client.execute(ExecuteRequest(promptSlug = "x"))

        val sent = engine.requestHistory.first()
        assertEquals("/api/v1/gateway/execute", sent.url.encodedPath)
        assertEquals("api.prompthelm.app", sent.url.host)
        client.close()
    }

    @Test
    fun `execute respects custom baseUrl`() = runTest {
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.OK, successBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            baseUrl = "https://staging.prompthelm.app",
            httpEngine = engine,
        )

        client.execute(ExecuteRequest(promptSlug = "x"))

        val sent = engine.requestHistory.first()
        assertEquals("staging.prompthelm.app", sent.url.host)
        assertEquals("/api/v1/gateway/execute", sent.url.encodedPath)
        client.close()
    }

    @Test
    fun `execute throws AuthenticationException on 401`() = runTest {
        val errorBody = """{"statusCode":401,"errorCode":"UNAUTHORIZED","message":"Invalid API key.","timestamp":"2026-06-05T10:30:00.000Z","requestId":"req-401"}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.Unauthorized, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "x")) }.exceptionOrNull()
        assertTrue(ex is AuthenticationException, "expected AuthenticationException, got $ex")
        ex as AuthenticationException
        assertEquals(401, ex.statusCode)
        assertEquals("UNAUTHORIZED", ex.errorCode)
        assertEquals("req-401", ex.requestId)
        assertEquals("Invalid API key.", ex.message)
        client.close()
    }

    @Test
    fun `execute throws AuthorizationException on 403`() = runTest {
        val errorBody = """{"statusCode":403,"error":"Forbidden","message":"No access to this prompt."}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.Forbidden, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "x")) }.exceptionOrNull()
        assertTrue(ex is AuthorizationException)
        assertEquals(403, (ex as AuthorizationException).statusCode)
        client.close()
    }

    @Test
    fun `execute throws NotFoundException on 404`() = runTest {
        val errorBody = """{"statusCode":404,"error":"Not Found","message":"Prompt not found."}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.NotFound, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "missing")) }.exceptionOrNull()
        assertTrue(ex is NotFoundException)
        client.close()
    }

    @Test
    fun `execute throws RateLimitException on 429`() = runTest {
        val errorBody = """{"statusCode":429,"error":"Too Many Requests","message":"slow down"}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.TooManyRequests, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "x")) }.exceptionOrNull()
        assertTrue(ex is RateLimitException)
        client.close()
    }

    @Test
    fun `execute throws ApiException on 500 after exhausting retries`() = runTest {
        val errorBody = """{"statusCode":500,"error":"Internal Server Error","message":"boom"}"""
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.InternalServerError, errorBody),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            maxRetries = 0,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "x")) }.exceptionOrNull()
        assertTrue(ex is ApiException)
        assertEquals(500, (ex as ApiException).statusCode)
        client.close()
    }

    @Test
    fun `execute falls back when error envelope is missing`() = runTest {
        val engine = MockHttpClient.engine(
            MockHttpClient.jsonResponse(HttpStatusCode.InternalServerError, ""),
        )
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            maxRetries = 0,
            httpEngine = engine,
        )

        val ex = runCatching { client.execute(ExecuteRequest(promptSlug = "x")) }.exceptionOrNull()
        assertTrue(ex is ApiException)
        assertEquals(500, (ex as ApiException).statusCode)
        assertNotNull(ex.message)
        client.close()
    }
}
