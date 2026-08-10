package com.tta.decisionassistant

import com.tta.decisionassistant.service.ResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseParserTest {

    @Test
    fun `parses well-formed LLM output`() {
        val raw = """
            EXPLANATION: This tour from Alpine Guides is the cheapest option at 1199 per person,
            and a private-guide ratio of 1:4 keeps the group very small — a strong match for travelers
            who prioritized price and privacy.
            POINTS:
            - Lowest pricePerPerson in the set at 1199
            - Small guide-to-traveler ratio of 1:4
            - Only 6 inclusions, a lean and private experience
        """.trimIndent()

        val r = ResponseParser.parse(raw).getOrThrow()
        assertTrue(r.explanation.startsWith("This tour from Alpine Guides"))
        assertEquals(3, r.keyPoints.size)
        assertTrue(r.keyPoints[0].contains("1199"))
    }

    @Test
    fun `fails when explanation missing`() {
        val r = ResponseParser.parse("POINTS:\n- foo\n- bar\n- baz")
        assertTrue(r.isFailure)
    }
}
