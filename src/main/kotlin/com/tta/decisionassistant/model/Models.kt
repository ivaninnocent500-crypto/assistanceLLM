package com.tta.decisionassistant.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Request ----

@Serializable
data class ExplainRequest(
    val priorities: List<String>,
    val recommendedTourId: String,
    val tours: List<Tour>
)

@Serializable
data class Tour(
    val tourId: String,
    val operatorName: String,
    val pricePerPerson: Double,
    val inclusionsCount: Int,
    val operatorRating: Double,
    val operatorReviewCount: Int,
    val guideToTravelerRatio: String,
    @SerialName("emergencySupport24_7")
    val emergencySupport24x7: Boolean,
    val freeCancelDays: Int,
    val sustainabilityScore: Double
)

// ---- Response ----

@Serializable
data class ExplainResponse(
    val explanation: String,
    val keyPoints: List<String>
)

// ---- Anthropic Messages API ----

@Serializable
internal data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String,
    val temperature: Double = 0.0
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class AnthropicResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<AnthropicContentBlock> = emptyList(),
    val model: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
internal data class AnthropicContentBlock(
    val type: String,
    val text: String? = null
)

@Serializable
internal data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)
