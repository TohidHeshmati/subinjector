package com.subinjector.enrichment.job

import com.subinjector.ai.LanguageModelException
import com.subinjector.enrichment.CueEnricher
import com.subinjector.enrichment.CueEnrichmentException
import com.subinjector.enrichment.CueEnrichmentRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EnrichmentJobWorker(
    private val service: EnrichmentJobService,
    private val cueEnricher: CueEnricher,
    @Value("\${subinjector.enrichment.worker.enabled:true}") private val workerEnabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recoverInterruptedCues() {
        if (workerEnabled) service.recoverInterruptedWork()
    }

    @Scheduled(
        fixedDelayString = "\${subinjector.enrichment.worker.poll-delay:500}",
        initialDelayString = "\${subinjector.enrichment.worker.initial-delay:5000}",
    )
    fun processOneCue() {
        val claimed = service.claimNextTask() ?: return
        try {
            val result = cueEnricher.enrich(
                CueEnrichmentRequest(
                    targetCue = claimed.cue,
                    previousCue = claimed.previousCue,
                    nextCue = claimed.nextCue,
                    learningLanguage = claimed.learningLanguage,
                    learnerLevel = claimed.learnerLevel,
                ),
            )
            service.saveSuccess(claimed.taskId, result)
            logger.debug("Saved enrichment for cue {} in job {}", claimed.cue.sequenceNumber, claimed.jobId)
        } catch (exception: CueEnrichmentException) {
            skip(claimed, exception)
        } catch (exception: LanguageModelException) {
            skip(claimed, exception)
        }
    }

    private fun skip(claimed: ClaimedCue, exception: Exception) {
        service.saveSkipped(claimed.taskId, exception.message ?: "No failure detail available")
        logger.warn(
            "Skipped cue enrichment: jobId={}, sequenceNumber={}, failureType={}, reason={}",
            claimed.jobId,
            claimed.cue.sequenceNumber,
            exception.javaClass.simpleName,
            exception.message ?: "No failure detail available",
        )
    }
}

