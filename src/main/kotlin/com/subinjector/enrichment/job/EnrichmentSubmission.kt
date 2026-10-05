package com.subinjector.enrichment.job

import java.util.UUID

data class EnrichmentSubmission(
    val documentId: UUID,
    val jobId: UUID,
    val status: EnrichmentJobStatus,
    val cueCount: Int,
)
