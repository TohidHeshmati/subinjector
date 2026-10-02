package com.subinjector.enrichment

import com.subinjector.ai.LanguageModelException
import com.subinjector.subtitle.SubtitleEntry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubtitleEnrichmentService(private val cueEnricher: CueEnricher) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun enrich(
        cues: List<SubtitleEntry>,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): List<SubtitleCueEnrichment> {
        val startedAt = System.nanoTime()
        val results = cues.mapIndexed { index, cue ->
            val cueStartedAt = System.nanoTime()
            val request = CueEnrichmentRequest(
                targetCue = cue,
                previousCue = cues.getOrNull(index - 1),
                nextCue = cues.getOrNull(index + 1),
                learningLanguage = learningLanguage,
                learnerLevel = learnerLevel,
            )

            try {
                val enrichment = cueEnricher.enrich(request)
                logger.debug(
                    "Enriched subtitle cue: sequenceNumber={}, noteCount={}, elapsedMs={}",
                    cue.sequenceNumber,
                    enrichment.notes.size,
                    elapsedMillisSince(cueStartedAt),
                )
                SubtitleCueEnrichment(cue, CueEnrichmentStatus.SUCCEEDED, enrichment)
            } catch (exception: CueEnrichmentException) {
                skipped(cue, exception, elapsedMillisSince(cueStartedAt))
            } catch (exception: LanguageModelException) {
                skipped(cue, exception, elapsedMillisSince(cueStartedAt))
            }
        }

        logger.info(
            "Completed subtitle enrichment: cues={}, succeeded={}, skipped={}, elapsedMs={}",
            results.size,
            results.count { it.status == CueEnrichmentStatus.SUCCEEDED },
            results.count { it.status == CueEnrichmentStatus.SKIPPED },
            elapsedMillisSince(startedAt),
        )
        return results
    }

    private fun skipped(
        cue: SubtitleEntry,
        exception: Exception,
        elapsedMillis: Long,
    ): SubtitleCueEnrichment {
        logger.warn(
            "Skipped subtitle cue enrichment: sequenceNumber={}, failureType={}, elapsedMs={}",
            cue.sequenceNumber,
            exception.javaClass.simpleName,
            elapsedMillis,
        )
        return SubtitleCueEnrichment(cue, CueEnrichmentStatus.SKIPPED, null)
    }

    private fun elapsedMillisSince(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
