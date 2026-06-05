package app.prompthelm.examples

import app.prompthelm.sdk.ApiException
import app.prompthelm.sdk.AuthenticationException
import app.prompthelm.sdk.ExecuteRequest
import app.prompthelm.sdk.PromptHelm
import app.prompthelm.sdk.RateLimitException
import app.prompthelm.sdk.StreamEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Minimal sample showing both `execute` and `stream`. Drop into an
 * Android `ViewModel` and replace `runBlocking` with `viewModelScope.launch`.
 *
 * The hard-coded key is a placeholder; pull yours from secure storage
 * (Android EncryptedSharedPreferences or BuildConfig).
 */
public object BasicUsage {

    private const val DEMO_KEY = "phk_0123456789abcdef0123456789abcdef" // replace me

    @JvmStatic
    public fun main(args: Array<String>) {
        runBlocking {
            PromptHelm(
                apiKey = DEMO_KEY,
                userAgent = "demo-app/0.0.1",
            ).use { client ->
                runExecute(client)
                runStream(client)
            }
        }
    }

    private suspend fun runExecute(client: PromptHelm) {
        try {
            val response = client.execute(
                ExecuteRequest(
                    promptSlug = "support-triage",
                    variables = mapOf("ticket" to "My subscription was charged twice."),
                ),
            )
            println("output=${response.output}")
            println("model=${response.model} cost=${response.cost} tokens=${response.totalTokens}")
        } catch (e: AuthenticationException) {
            println("Bad API key. requestId=${e.requestId}")
        } catch (e: RateLimitException) {
            println("Rate limited. Back off and retry later.")
        } catch (e: ApiException) {
            println("API failed: status=${e.statusCode} errorCode=${e.errorCode} message=${e.message}")
        }
    }

    private suspend fun runStream(client: PromptHelm) {
        client.stream(ExecuteRequest(promptSlug = "support-triage")).collect { event ->
            when (event) {
                is StreamEvent.Chunk -> print(event.content)
                is StreamEvent.Done -> println("\n[done] cost=${event.cost} tokens=${event.totalTokens}")
                is StreamEvent.Err -> println("\n[error] code=${event.errorCode} message=${event.message}")
            }
        }
    }
}
