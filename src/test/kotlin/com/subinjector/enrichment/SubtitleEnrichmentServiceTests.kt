package com.subinjector.enrichment

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelException
import com.subinjector.ai.LanguageModelOutputFormat
import com.subinjector.enrichment.prompt.CueEnrichmentPrompt
import com.subinjector.subtitle.SubtitleEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class SubtitleEnrichmentServiceTests {
    @Test
    fun `enriches cues in order with adjacent context and skips a failure without dropping the cue`() {
        val prompt = RecordingPrompt()
        val model = StubLanguageModel { cueNumber ->
            if (cueNumber == 2) {
                "not json"
            } else {
                """{"cueNumber":$cueNumber,"notes":${if (cueNumber == 1) "[]" else "[{\"category\":\"idiom\",\"expression\":\"Wort\",\"explanation\":\"A phrase.\"}]"}}"""
            }
        }
        val service = service(prompt, model)
        val cues = (1..3).map(::cue)

        val results = service.enrich(cues, LearningLanguage.GERMAN, CefrLevel.B1)

        assertEquals(cues, results.map { it.cue })
        assertEquals(
            listOf(CueEnrichmentStatus.SUCCEEDED, CueEnrichmentStatus.SKIPPED, CueEnrichmentStatus.SUCCEEDED),
            results.map { it.status },
        )
        assertEquals(emptyList<EnrichmentNote>(), results[0].enrichment?.notes)
        assertNull(results[1].enrichment)
        assertEquals(3, results[2].enrichment?.cueNumber)
        assertEquals(listOf(1, 2, 3), prompt.requests.map { it.targetCue.sequenceNumber })
        assertEquals(setOf(LearningLanguage.GERMAN), prompt.requests.map { it.learningLanguage }.toSet())
        assertEquals(setOf(CefrLevel.B1), prompt.requests.map { it.learnerLevel }.toSet())
        assertEquals(null, prompt.requests.first().previousCue)
        assertEquals(cues[1], prompt.requests.first().nextCue)
        assertEquals(cues[0], prompt.requests[1].previousCue)
        assertEquals(cues[2], prompt.requests[1].nextCue)
        assertEquals(cues[1], prompt.requests.last().previousCue)
        assertEquals(null, prompt.requests.last().nextCue)
    }

    @Test
    fun `skips a provider failure and continues with later cues`() {
        val prompt = RecordingPrompt()
        val model = StubLanguageModel { cueNumber ->
            if (cueNumber == 2) throw LanguageModelException("Local model unavailable")
            """{"cueNumber":$cueNumber,"notes":[]}"""
        }
        val service = service(prompt, model)

        val results = service.enrich((1..3).map(::cue), LearningLanguage.GERMAN, CefrLevel.B1)

        assertEquals(
            listOf(CueEnrichmentStatus.SUCCEEDED, CueEnrichmentStatus.SKIPPED, CueEnrichmentStatus.SUCCEEDED),
            results.map { it.status },
        )
        assertEquals(listOf(1, 2, 3), prompt.requests.map { it.targetCue.sequenceNumber })
    }

    @Test
    fun `processes a large subtitle cue list sequentially in original order`() {
        val prompt = RecordingPrompt()
        val model = StubLanguageModel { cueNumber -> """{"cueNumber":$cueNumber,"notes":[]}""" }
        val service = service(prompt, model)
        val cues = (1..2_000).map(::cue)

        val results = service.enrich(cues, LearningLanguage.GERMAN, CefrLevel.B1)

        assertEquals(2_000, results.size)
        assertEquals((1..2_000).toList(), results.map { it.cue.sequenceNumber })
        assertEquals((1..2_000).toList(), prompt.requests.map { it.targetCue.sequenceNumber })
        assertEquals(2_000, model.outputBudgets.size)
        assertEquals(setOf(512), model.outputBudgets.toSet())
    }

    private fun service(prompt: RecordingPrompt, model: StubLanguageModel) = SubtitleEnrichmentService(
        CueEnricher(model, listOf(prompt), ObjectMapper()),
    )

    private fun cue(sequenceNumber: Int) = SubtitleEntry(
        sequenceNumber = sequenceNumber,
        startTime = "00:00:01,000",
        endTime = "00:00:02,000",
        text = "Cue $sequenceNumber",
    )

    private class RecordingPrompt : CueEnrichmentPrompt {
        val requests = mutableListOf<CueEnrichmentRequest>()

        override val learningLanguage = LearningLanguage.GERMAN

        override fun build(request: CueEnrichmentRequest): String {
            requests += request
            return request.targetCue.sequenceNumber.toString()
        }
    }

    private class StubLanguageModel(
        private val responseForCue: (Int) -> String,
    ) : LanguageModel {
        val outputBudgets = mutableListOf<Int>()

        override fun generate(
            prompt: String,
            maxOutputTokens: Int,
            outputFormat: LanguageModelOutputFormat,
        ): String {
            outputBudgets += maxOutputTokens
            return responseForCue(prompt.toInt())
        }
    }
}
