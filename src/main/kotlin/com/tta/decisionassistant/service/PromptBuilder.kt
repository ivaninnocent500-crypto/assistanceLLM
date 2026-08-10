package com.tta.decisionassistant.service

import com.tta.decisionassistant.model.ExplainRequest
import com.tta.decisionassistant.model.Tour

object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are a travel recommendation EXPLAINER. Your sole job is to explain, in plain English, why an ALREADY-CHOSEN tour fits the traveler's stated priorities.

ABSOLUTE RULES (do not violate):
1. Never recommend a different tour. The decision is already made.
2. Use ONLY the numeric / string facts in the supplied payload. Never invent numbers, operators, features, or guarantees that are not explicitly present.
3. Do not mention other tours in the input even if they would look better against the same priorities — that comparison is not your job.
4. Keep the explanation to 2–3 sentences. Keep each bullet to one short clause (about 15 words or fewer).

PRIORITY INTERPRETATIONS:
- PRICE: lower pricePerPerson matters most.
- PRIVACY: smaller groups (a guide-to-traveler ratio closer to 1:1, e.g. 1:4 instead of 1:12) and fewer inclusions matter most.
- COMFORT: higher operatorRating and 24/7 emergency support matter most.
- FLEXIBILITY: more freeCancelDays matters most.
- SUSTAINABILITY: higher sustainabilityScore matters most.
- INCLUSIONS: higher inclusionsCount matters most.

OUTPUT FORMAT — respond with EXACTLY this structure, nothing before or after:

EXPLANATION: <2-3 sentences of natural language>
POINTS:
- <short factual point 1>
- <short factual point 2>
- <short factual point 3>
"""

    fun systemPrompt(): String = SYSTEM_PROMPT

    fun userPrompt(req: ExplainRequest, recommended: Tour): String = buildString {
        appendLine("Traveler's priorities (in order): ${req.priorities.joinToString(", ")}")
        appendLine()
        appendLine("The tour chosen for this traveler (already decided by the booking system):")
        appendLine("tourId=${recommended.tourId}")
        appendLine("operatorName=${recommended.operatorName}")
        appendLine("pricePerPerson=${recommended.pricePerPerson}")
        appendLine("inclusionsCount=${recommended.inclusionsCount}")
        appendLine("operatorRating=${recommended.operatorRating}")
        appendLine("operatorReviewCount=${recommended.operatorReviewCount}")
        appendLine("guideToTravelerRatio=${recommended.guideToTravelerRatio}")
        appendLine("emergencySupport24_7=${recommended.emergencySupport24x7}")
        appendLine("freeCancelDays=${recommended.freeCancelDays}")
        appendLine("sustainabilityScore=${recommended.sustainabilityScore}")
        appendLine()
        append("Explain in the required format why THIS tour fits the priorities above. Use ONLY the facts listed here.")
    }
}
