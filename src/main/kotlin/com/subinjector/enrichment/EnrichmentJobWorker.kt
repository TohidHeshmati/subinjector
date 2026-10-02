package com.subinjector.enrichment

import com.subinjector.ai.LanguageModelException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value

@Component
class EnrichmentJobWorker(
    private val store: EnrichmentJobStore,
    private val cueEnricher: CueEnricher,
    @Value("\${subinjector.enrichment.worker.enabled:true}") private val workerEnabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recoverInterruptedCues() {
        if (workerEnabled) store.recoverInterruptedWork()
    }

    @Scheduled(
        fixedDelayString = "\${subinjector.enrichment.worker.poll-delay:500}",
        initialDelayString = "\${subinjector.enrichment.worker.initial-delay:5000}",
    )
    fun processOneCue() {
        val claimed = store.claimNextCue() ?: return
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
            store.saveSuccess(claimed.jobId, claimed.cue.sequenceNumber, result)
            logger.debug("Saved enrichment for cue {} in job {}", claimed.cue.sequenceNumber, claimed.jobId)
        } catch (exception: CueEnrichmentException) {
            skip(claimed, exception)
        } catch (exception: LanguageModelException) {
            skip(claimed, exception)
        }
    }

    private fun skip(claimed: ClaimedCue, exception: Exception) {
        store.saveSkipped(claimed.jobId, claimed.cue.sequenceNumber)
        logger.warn(
            "Skipped cue enrichment: jobId={}, sequenceNumber={}, failureType={}, reason={}",
            claimed.jobId,
            claimed.cue.sequenceNumber,
            exception.javaClass.simpleName,
            exception.message ?: "No failure detail available",
        )
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["subinjector.enrichment.worker.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class EnrichmentWorkerSchedulingConfiguration
