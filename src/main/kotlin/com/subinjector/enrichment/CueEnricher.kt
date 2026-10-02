package com.subinjector.enrichment

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelOutputFormat
import com.subinjector.enrichment.prompt.CueEnrichmentPrompt
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
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
        val generatedResponse = languageModel.generate(
            prompt = prompt.build(request),
            maxOutputTokens = MAX_OUTPUT_TOKENS,
            outputFormat = LanguageModelOutputFormat.JSON,
        )
        return parseResponse(generatedResponse, request.targetCue.sequenceNumber)
    }

    private fun parseResponse(response: String, expectedCueNumber: Int): CueEnrichment {
        val result = try {
            objectMapper.readTree(response)
        } catch (exception: JacksonException) {
            throw CueEnrichmentException("Language model returned invalid JSON", exception)
        } ?: throw CueEnrichmentException("Language model response must be a JSON object")

        result.requireExactFields(EXPECTED_RESPONSE_FIELDS, "Language model response")

        val cueNumber = result["cueNumber"]
            ?.takeIf { it.isIntegralNumber && it.canConvertToInt() }
            ?.intValue()
            ?: throw CueEnrichmentException("Language model response is missing a valid cueNumber")
        if (cueNumber != expectedCueNumber) {
            throw CueEnrichmentException("Language model response refers to a different cue")
        }

        val notes = result["notes"]
            ?.takeIf(JsonNode::isArray)
            ?: throw CueEnrichmentException("Language model response is missing a notes array")
        if (notes.size() > MAX_NOTES_PER_CUE) {
            throw CueEnrichmentException("Language model returned more than $MAX_NOTES_PER_CUE notes")
        }

        return CueEnrichment(cueNumber, notes.toList().map(::parseNote))
    }

    private fun parseNote(note: JsonNode): EnrichmentNote {
        note.requireExactFields(EXPECTED_NOTE_FIELDS, "Enrichment note")

        val category = when (note.requiredNonBlankText("category")) {
            "vocabulary" -> EnrichmentCategory.VOCABULARY
            "idiom" -> EnrichmentCategory.IDIOM
            "grammar" -> EnrichmentCategory.GRAMMAR
            else -> throw CueEnrichmentException("Enrichment note has an unsupported category")
        }
        val expression = note.requiredNonBlankText("expression")
        val explanation = note.requiredNonBlankText("explanation")
        if (explanation.length > MAX_EXPLANATION_LENGTH || sentenceCount(explanation) > MAX_SENTENCES_PER_EXPLANATION) {
            throw CueEnrichmentException("Enrichment note explanation exceeds the length limit")
        }

        return EnrichmentNote(category, expression, explanation)
    }

    private fun JsonNode.requireExactFields(expected: Set<String>, subject: String) {
        if (!isObject) throw CueEnrichmentException("$subject must be a JSON object")
        if (propertyNames().toSet() != expected) {
            throw CueEnrichmentException("$subject has unexpected or missing fields")
        }
    }

    private fun JsonNode.requiredNonBlankText(field: String): String = get(field)
        ?.takeIf(JsonNode::isString)
        ?.stringValue()
        ?.takeIf(String::isNotBlank)
        ?: throw CueEnrichmentException("Enrichment note $field must be a non-blank string")

    private fun sentenceCount(text: String): Int = text.count { it == '.' || it == '!' || it == '?' }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 512
        const val MAX_NOTES_PER_CUE = 3
        const val MAX_EXPLANATION_LENGTH = 300
        const val MAX_SENTENCES_PER_EXPLANATION = 2
        val EXPECTED_RESPONSE_FIELDS = setOf("cueNumber", "notes")
        val EXPECTED_NOTE_FIELDS = setOf("category", "expression", "explanation")
    }
}
