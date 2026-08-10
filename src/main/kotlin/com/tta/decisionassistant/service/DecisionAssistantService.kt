package com.tta.decisionassistant.service

import com.tta.decisionassistant.model.ExplainRequest
import com.tta.decisionassistant.model.ExplainResponse
import org.slf4j.LoggerFactory

/**
 * Orchestrates a single explain() request: validates the payload, calls Claude, and
 * parses the strict EXPLANATION:/POINTS:- response. Any failure inside this method
 * collapses into a Result.failure so callers can map cleanly to HTTP 503.
 */
class DecisionAssistantService(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-6"
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(DecisionAssistantService::class.java)
    private val client = ClaudeClient(apiKey = apiKey, model = model)

    suspend fun explain(req: ExplainRequest): Result<ExplainResponse> = runCatching {
        val recommended = req.tours.firstOrNull { it.tourId == req.recommendedTourId }
            ?: throw NoSuchElementException(
                "recommendedTourId='${req.recommendedTourId}' was not found in the supplied tours list"
            )

        val raw = client.complete(
            systemPrompt = PromptBuilder.systemPrompt(),
            userPrompt = PromptBuilder.userPrompt(req, recommended)
        )
        log.debug("LLM raw output for tour {}: {}", recommended.tourId, raw.take(400))

        ResponseParser.parse(raw).getOrElse {
            throw IllegalStateException("LLM output did not match required format: ${it.message}", it)
        }
    }

    override fun close() {
        client.close()
    }
}
