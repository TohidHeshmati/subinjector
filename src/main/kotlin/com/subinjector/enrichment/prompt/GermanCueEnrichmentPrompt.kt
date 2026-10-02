package com.subinjector.enrichment.prompt

import com.subinjector.enrichment.CueEnrichmentRequest
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.subtitle.SubtitleCue
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class GermanCueEnrichmentPrompt(
    @Value("classpath:prompts/enrichment/german-cue-enrichment.md") promptResource: Resource,
    private val objectMapper: ObjectMapper,
) : CueEnrichmentPrompt {
    override val learningLanguage = LearningLanguage.GERMAN
    private val promptTemplate = PromptTemplate.builder().resource(promptResource).build()

    override fun build(request: CueEnrichmentRequest): String {
        val subtitleContext = objectMapper.writeValueAsString(
            mapOf(
                "previousCue" to request.previousCue.toPromptData(),
                "targetCue" to request.targetCue.toPromptData(),
                "nextCue" to request.nextCue.toPromptData(),
            ),
        )
        return promptTemplate.render(
            mapOf(
                "learnerLevel" to request.learnerLevel,
                "subtitleContext" to subtitleContext,
            ),
        )
    }

    private fun SubtitleCue?.toPromptData(): Map<String, Any?>? = this?.let {
        mapOf(
            "sequenceNumber" to it.sequenceNumber,
            "startTime" to formatMs(it.startMs),
            "endTime" to formatMs(it.endMs),
            "text" to it.text,
        )
    }

    private fun formatMs(ms: Int): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
