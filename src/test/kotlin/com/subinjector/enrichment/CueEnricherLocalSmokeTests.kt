package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleCue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_SMOKE_TEST", matches = "true")
class CueEnricherLocalSmokeTests {
    @Autowired
    private lateinit var cueEnricher: CueEnricher

    @Test
    fun `enriches one German cue with a valid bounded response`() {
        val result = cueEnricher.enrich(
            CueEnrichmentRequest(
                targetCue = cue(2, "Ich verstehe nur Bahnhof."),
                previousCue = cue(1, "Was hast du gesagt?"),
                nextCue = cue(3, "Ich erkläre es dir noch einmal."),
                learningLanguage = LearningLanguage.GERMAN,
                learnerLevel = CefrLevel.B1,
            ),
        )

        assertEquals(2, result.cueNumber)
        assertTrue(result.notes.size <= 3)
        assertTrue(result.notes.all { it.expression.isNotBlank() && it.explanation.isNotBlank() })
    }

    private fun cue(number: Int, text: String) = SubtitleCue(
        sequenceNumber = number,
        startMs = 1_000,
        endMs = 2_000,
        text = text,
    )
}
