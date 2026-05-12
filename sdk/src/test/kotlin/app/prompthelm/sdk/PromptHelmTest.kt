package app.prompthelm.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Constructor / configuration validation tests. These don't need
 * the network, so they can run with a no-op MockEngine.
 */
class PromptHelmTest {

    private fun engine() = MockEngine { respondOk("{}") }

    @Test
    fun `accepts a valid API key and exposes SDK_VERSION`() {
        val client = PromptHelm(
            apiKey = MockHttpClient.TEST_API_KEY,
            httpEngine = engine(),
        )
        assertEquals("0.1.0", PromptHelm.SDK_VERSION)
        client.close()
    }

    @Test
    fun `rejects an empty API key`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(apiKey = "", httpEngine = engine())
        }
        assertTrue(ex.message!!.contains("apiKey"))
    }

    @Test
    fun `rejects an API key without phk_ prefix`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(
                apiKey = "sk_0123456789abcdef0123456789abcdef00",
                httpEngine = engine(),
            )
        }
        assertTrue(ex.message!!.contains("phk_"))
    }

    @Test
    fun `rejects an API key with wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(
                apiKey = "phk_short",
                httpEngine = engine(),
            )
        }
    }

    @Test
    fun `rejects a non-http baseUrl`() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(
                apiKey = MockHttpClient.TEST_API_KEY,
                baseUrl = "ws://api.prompthelm.app",
                httpEngine = engine(),
            )
        }
    }

    @Test
    fun `rejects a non-positive timeout`() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(
                apiKey = MockHttpClient.TEST_API_KEY,
                timeoutMillis = 0,
                httpEngine = engine(),
            )
        }
    }

    @Test
    fun `rejects negative maxRetries`() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptHelm(
                apiKey = MockHttpClient.TEST_API_KEY,
                maxRetries = -1,
                httpEngine = engine(),
            )
        }
    }

    @Test
    fun `accepts a config object via secondary constructor`() {
        val client = PromptHelm(
            PromptHelmConfig(
                apiKey = MockHttpClient.TEST_API_KEY,
                httpEngine = engine(),
            ),
        )
        client.close()
    }

    @Test
    fun `default configuration uses production base URL`() {
        assertEquals("https://api.prompthelm.app", PromptHelmConfig.DEFAULT_BASE_URL)
        assertEquals(60_000L, PromptHelmConfig.DEFAULT_TIMEOUT_MILLIS)
        assertEquals(2, PromptHelmConfig.DEFAULT_MAX_RETRIES)
    }
}
