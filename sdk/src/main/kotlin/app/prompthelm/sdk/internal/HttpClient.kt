package app.prompthelm.sdk.internal

import app.prompthelm.sdk.ApiException
import app.prompthelm.sdk.ErrorEnvelope
import app.prompthelm.sdk.ErrorMapper
import app.prompthelm.sdk.ExecuteRequest
import app.prompthelm.sdk.ExecuteResponse
import app.prompthelm.sdk.PromptHelmConfig
import app.prompthelm.sdk.PromptHelmException
import app.prompthelm.sdk.PromptHelmTimeoutException
import app.prompthelm.sdk.StreamEvent
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Internal Ktor wrapper. Owns the lifecycle of the underlying
 * Ktor `io.ktor.client.HttpClient` and translates HTTP / network
 * failures into the public [PromptHelmException] hierarchy.
 *
 * Caller is responsible for [close]ing the wrapper when finished.
 */
internal class HttpClient(
    private val config: PromptHelmConfig,
    private val sdkVersion: String,
) : AutoCloseable {

    private val baseUrl: String = config.baseUrl.trimEnd('/')
    private val userAgent: String = buildUserAgent(config.userAgent, sdkVersion)

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val client: io.ktor.client.HttpClient = createKtorClient(config.httpEngine)

    suspend fun execute(request: ExecuteRequest): ExecuteResponse {
        val response = try {
            client.post("$baseUrl/api/v1/gateway/execute") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: HttpRequestTimeoutException) {
            throw PromptHelmTimeoutException(config.timeoutMillis, cause = e)
        }

        if (!response.status.isSuccess()) {
            throw mapErrorResponse(response.status.value, response.bodyAsText())
        }

        return try {
            response.body()
        } catch (e: SerializationException) {
            throw ApiException(
                statusCode = response.status.value,
                errorCode = "INVALID_RESPONSE",
                requestId = response.headers["x-request-id"],
                message = "Failed to deserialize gateway response: ${e.message}",
                cause = e,
            )
        }
    }

    fun stream(request: ExecuteRequest): Flow<StreamEvent> = flow {
        val statement: HttpStatement = client.preparePost("$baseUrl/api/v1/gateway/stream") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(request)
        }

        try {
            statement.execute { response ->
                if (!response.status.isSuccess()) {
                    throw mapErrorResponse(response.status.value, response.bodyAsText())
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                val parser = SseParser()

                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    val event = parser.feed(line) ?: continue
                    emit(event)
                    if (event is StreamEvent.Done || event is StreamEvent.Err) {
                        return@execute
                    }
                }
                parser.flush()?.let { emit(it) }
            }
        } catch (e: HttpRequestTimeoutException) {
            throw PromptHelmTimeoutException(config.timeoutMillis, cause = e)
        }
    }

    override fun close() {
        client.close()
    }

    private fun mapErrorResponse(status: Int, body: String): PromptHelmException {
        val envelope: ErrorEnvelope? = if (body.isBlank()) {
            null
        } else {
            try {
                json.decodeFromString(ErrorEnvelope.serializer(), body)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return ErrorMapper.fromHttp(status, envelope)
    }

    private fun createKtorClient(injectedEngine: HttpClientEngine?): io.ktor.client.HttpClient {
        val configBlock: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeoutMillis
                connectTimeoutMillis = config.timeoutMillis
                // Streams may stay open longer than the request timeout — let
                // the caller's coroutine cancel them instead of an arbitrary
                // socket read deadline. Long.MAX_VALUE is Ktor's "no timeout".
                socketTimeoutMillis = Long.MAX_VALUE
            }
            defaultRequest {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }
            expectSuccess = false
        }
        return if (injectedEngine != null) {
            io.ktor.client.HttpClient(injectedEngine, configBlock)
        } else {
            io.ktor.client.HttpClient(OkHttp, configBlock)
        }
    }

    internal companion object {
        private const val SDK_UA_BASE = "prompt-helm-sdk-android"

        internal fun buildUserAgent(prefix: String?, sdkVersion: String): String {
            val core = "$SDK_UA_BASE/$sdkVersion"
            return if (prefix.isNullOrBlank()) core else "$prefix $core"
        }
    }
}
