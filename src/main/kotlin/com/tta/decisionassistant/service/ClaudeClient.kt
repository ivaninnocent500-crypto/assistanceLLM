package com.tta.decisionassistant.service

import com.tta.decisionassistant.model.AnthropicMessage
import com.tta.decisionassistant.model.AnthropicRequest
import com.tta.decisionassistant.model.AnthropicResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin client for the Anthropic Messages API. Uses Ktor (CIO) + kotlinx-serialization only,
 * no Gson anywhere. The API key is read from configuration and never appears in logs or
 * responses. The Anthropic-version header is required by the Messages API.
 */
class ClaudeClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-6",
    private val requestTimeoutMillis: Long = 15_000L,
    private val connectTimeoutMillis: Long = 5_000L,
    private val baseUrl: String = "https://api.anthropic.com"
) : AutoCloseable {

    class ClaudeCallException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ClientContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeoutMillis
            connectTimeoutMillis = connectTimeoutMillis
            socketTimeoutMillis = requestTimeoutMillis
        }
    }

    suspend fun complete(systemPrompt: String, userPrompt: String): String {
        if (apiKey.isBlank()) {
            throw ClaudeCallException("ANTHROPIC_API_KEY is not configured")
        }
        val request = AnthropicRequest(
            model = model,
            max_tokens = 500,
            system = systemPrompt,
            temperature = 0.0,
            messages = listOf(AnthropicMessage(role = "user", content = userPrompt))
        )

        val response = client.post("$baseUrl/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ClaudeCallException("Anthropic returned ${response.status.value}: $body")
        }

        val parsed = runCatching { json.decodeFromString(AnthropicResponse.serializer(), response.bodyAsText()) }
            .getOrElse { throw ClaudeCallException("could not parse Anthropic response", it) }

        val text = parsed.content.firstOrNull { it.type == "text" }?.text
            ?: throw ClaudeCallException("no text content block in Anthropic response")

        return text
    }

    override fun close() {
        client.close()
    }

    companion object {
        /** Pinned Messages API version header. */
        const val ANTHROPIC_VERSION: String = "2023-06-01"
    }
}
