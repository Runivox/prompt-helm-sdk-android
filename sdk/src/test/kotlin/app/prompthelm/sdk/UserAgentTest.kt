package app.prompthelm.sdk

import app.prompthelm.sdk.internal.HttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserAgentTest {

    @Test
    fun `default user agent is the SDK identifier`() {
        val ua = HttpClient.buildUserAgent(null, "0.1.0")
        assertEquals("prompt-helm-sdk-android/0.1.0", ua)
    }

    @Test
    fun `custom prefix is prepended to SDK identifier`() {
        val ua = HttpClient.buildUserAgent("checkout-svc/1.4.2", "0.1.0")
        assertEquals("checkout-svc/1.4.2 prompt-helm-sdk-android/0.1.0", ua)
    }

    @Test
    fun `blank prefix is treated as null`() {
        val ua = HttpClient.buildUserAgent("   ", "0.1.0")
        assertEquals("prompt-helm-sdk-android/0.1.0", ua)
    }
}
