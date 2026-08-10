package com.tta.decisionassistant.service

import com.tta.decisionassistant.model.ExplainResponse

object ResponseParser {

    private const val EXPLANATION_TAG = "EXPLANATION:"
    private const val POINTS_TAG = "POINTS:"
    private val BULLET_PREFIXES = listOf("-", "\u2022", "*") // -, bullet, asterisk

    /**
     * Parses the strict `EXPLANATION: ...` followed by `POINTS:` bullet list format
     * into an [ExplainResponse]. The format is defined in [PromptBuilder]'s system
     * prompt and enforced at construction time.
     */
    fun parse(raw: String): Result<ExplainResponse> {
        if (raw.isBlank()) {
            return Result.failure(IllegalArgumentException("empty LLM output"))
        }

        val explanationStart = raw.indexOf(EXPLANATION_TAG)
        if (explanationStart < 0) {
            return Result.failure(IllegalArgumentException("missing EXPLANATION: header"))
        }

        val afterExplanation = explanationStart + EXPLANATION_TAG.length

        val pointsStartAbsolute = raw.indexOf(POINTS_TAG, afterExplanation)
        val explanationBody = if (pointsStartAbsolute >= 0) {
            raw.substring(afterExplanation, pointsStartAbsolute)
        } else {
            raw.substring(afterExplanation)
        }

        val explanation = explanationBody.replace('\r', ' ').replace('\n', ' ').trim()
        if (explanation.isEmpty()) {
            return Result.failure(IllegalArgumentException("empty EXPLANATION body"))
        }

        val points = if (pointsStartAbsolute >= 0) {
            val afterPoints = pointsStartAbsolute + POINTS_TAG.length
            raw.substring(afterPoints)
                .lineSequence()
                .map { it.trim() }
                .filter { line ->
                    BULLET_PREFIXES.any { line.startsWith(it) }
                }
                .map { line ->
                    BULLET_PREFIXES.fold(line) { acc, prefix ->
                        if (acc.startsWith(prefix)) acc.removePrefix(prefix).trimStart() else acc
                    }.trim()
                }
                .filter { it.isNotEmpty() }
                .take(5)
                .toList()
        } else {
            emptyList()
        }

        if (points.isEmpty()) {
            return Result.failure(IllegalArgumentException("no bullet points parsed"))
        }

        return Result.success(ExplainResponse(explanation = explanation, keyPoints = points))
    }
}
