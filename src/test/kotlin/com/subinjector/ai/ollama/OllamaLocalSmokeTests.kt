package com.subinjector.ai.ollama

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.CueEnricher
import com.subinjector.enrichment.CueEnrichmentRequest
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.subtitle.SubtitleCue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_SMOKE_TEST", matches = "true")
class OllamaLocalSmokeTests {
    @Autowired
    private lateinit var cueEnricher: CueEnricher

    @Test
    fun `returns validated structured enrichment through the configured local Ollama model`() {
        val result = cueEnricher.enrich(
            CueEnrichmentRequest(
                previousCue = cue(1, "Was hast du gesagt?"),
                targetCue = cue(2, "Ich verstehe nur Bahnhof."),
                nextCue = cue(3, "Dann erkläre ich es noch einmal."),
                learningLanguage = LearningLanguage.GERMAN,
                learnerLevel = CefrLevel.B1,
            ),
        )

        assertEquals(2, result.cueNumber)
        assert(result.notes.size <= 3)
    }

    private fun cue(number: Int, text: String) = SubtitleCue(
        sequenceNumber = number,
        startMs = 1_000,
        endMs = 2_000,
        text = text,
    )
}
