package app.prompthelm.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * Test helpers for assembling `MockEngine` handlers without
 * Ktor-version churn polluting the test bodies.
 */
internal object MockHttpClient {

    internal const val TEST_API_KEY = "phk_0123456789abcdef0123456789abcdef" // 36 chars
    internal const val OTHER_VALID_KEY = "phk_fedcba9876543210fedcba9876543210"

    internal fun jsonResponse(
        status: HttpStatusCode,
        body: String,
        requestId: String = "test-req-1",
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(
            content = body,
            status = status,
            headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "x-request-id" to listOf(requestId),
            ),
        )
    }

    internal fun sseResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(
            content = body,
            status = status,
            headers = headersOf(
                HttpHeaders.ContentType to listOf("text/event-stream"),
            ),
        )
    }

    internal fun engine(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MockEngine = MockEngine(handler)
}
