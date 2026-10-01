package com.subinjector.enrichment

import com.subinjector.ai.LanguageModel
import com.subinjector.enrichment.prompt.CueEnrichmentPrompt
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class CueEnricher(
    private val languageModel: LanguageModel,
    private val prompts: List<CueEnrichmentPrompt>,
    private val objectMapper: ObjectMapper,
) {
    fun enrich(request: CueEnrichmentRequest): CueEnrichment {
        val prompt = prompts.singleOrNull { it.learningLanguage == request.learningLanguage }
            ?: throw CueEnrichmentException("No unique prompt is configured for ${request.learningLanguage}")
        val generatedResponse = languageModel.generate(prompt.build(request))
        return parseResponse(generatedResponse, request.targetCue.sequenceNumber)
    }

    private fun parseResponse(response: String, expectedCueNumber: Int): CueEnrichment {
        val result = try {
            objectMapper.readValue(response, Map::class.java)
        } catch (exception: Exception) {
            throw CueEnrichmentException("Language model returned invalid JSON", exception)
        } ?: throw CueEnrichmentException("Language model response must be a JSON object")

        if (result.keys != EXPECTED_RESPONSE_FIELDS) {
            throw CueEnrichmentException("Language model response has unexpected or missing fields")
        }

        val rawCueNumber = result["cueNumber"] as? Number
            ?: throw CueEnrichmentException("Language model response is missing a valid cueNumber")
        val cueNumber = rawCueNumber.toInt()
        if (rawCueNumber.toDouble() != cueNumber.toDouble()) {
            throw CueEnrichmentException("Language model response is missing a valid cueNumber")
        }
        if (cueNumber != expectedCueNumber) {
            throw CueEnrichmentException("Language model response refers to a different cue")
        }

        val rawNotes = result["notes"] as? List<*>
            ?: throw CueEnrichmentException("Language model response is missing a notes array")
        if (rawNotes.size > MAX_NOTES_PER_CUE) {
            throw CueEnrichmentException("Language model returned more than $MAX_NOTES_PER_CUE notes")
        }

        return CueEnrichment(cueNumber, rawNotes.map(::parseNote))
    }

    private fun parseNote(value: Any?): EnrichmentNote {
        val fields = value as? Map<*, *>
            ?: throw CueEnrichmentException("Each enrichment note must be a JSON object")
        if (fields.keys != EXPECTED_NOTE_FIELDS) {
            throw CueEnrichmentException("Enrichment note has unexpected or missing fields")
        }

        val category = when (fields["category"]) {
            "vocabulary" -> EnrichmentCategory.VOCABULARY
            "idiom" -> EnrichmentCategory.IDIOM
            "grammar" -> EnrichmentCategory.GRAMMAR
            else -> throw CueEnrichmentException("Enrichment note has an unsupported category")
        }
        val expression = fields["expression"] as? String
            ?: throw CueEnrichmentException("Enrichment note is missing its expression")
        if (expression.isBlank()) throw CueEnrichmentException("Enrichment note expression must not be blank")

        val explanation = fields["explanation"] as? String
            ?: throw CueEnrichmentException("Enrichment note is missing its explanation")
        if (explanation.isBlank()) throw CueEnrichmentException("Enrichment note explanation must not be blank")
        if (explanation.length > MAX_EXPLANATION_LENGTH || sentenceCount(explanation) > MAX_SENTENCES_PER_EXPLANATION) {
            throw CueEnrichmentException("Enrichment note explanation exceeds the length limit")
        }

        return EnrichmentNote(category, expression, explanation)
    }

    private fun sentenceCount(text: String): Int = text.count { it == '.' || it == '!' || it == '?' }

    private companion object {
        const val MAX_NOTES_PER_CUE = 3
        const val MAX_EXPLANATION_LENGTH = 300
        const val MAX_SENTENCES_PER_EXPLANATION = 2
        val EXPECTED_RESPONSE_FIELDS = setOf("cueNumber", "notes")
        val EXPECTED_NOTE_FIELDS = setOf("category", "expression", "explanation")
    }
}
