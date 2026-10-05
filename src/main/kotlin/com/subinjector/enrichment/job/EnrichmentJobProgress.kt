package com.subinjector.enrichment.job

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import java.time.OffsetDateTime
import java.util.UUID

data class EnrichmentJobProgress(
    val jobId: UUID,
    val documentId: UUID,
    val language: LearningLanguage,
    val level: CefrLevel,
    val status: EnrichmentJobStatus,
    val cueCount: Int,
    val pendingCueCount: Int,
    val processingCueCount: Int,
    val succeededCueCount: Int,
    val skippedCueCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
