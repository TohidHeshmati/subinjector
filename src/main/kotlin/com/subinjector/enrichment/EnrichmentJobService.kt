package com.subinjector.enrichment

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EnrichmentJobService(private val store: EnrichmentJobStore) {
    fun createJob(
        documentId: UUID,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): EnrichmentSubmission = store.createJobForDocument(documentId, learningLanguage, learnerLevel)

    fun getJob(jobId: UUID): EnrichmentJobProgress =
        store.findJob(jobId) ?: throw EnrichmentJobNotFoundException(jobId.toString())

    fun getResults(jobId: UUID): List<EnrichmentJobCueResult> {
        getJob(jobId)
        return store.findResults(jobId)
    }
}
