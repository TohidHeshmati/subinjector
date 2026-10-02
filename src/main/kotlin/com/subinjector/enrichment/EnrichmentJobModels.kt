package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleCue
import java.time.OffsetDateTime
import java.util.UUID

data class EnrichmentSubmission(
    val documentId: UUID,
    val jobId: UUID,
    val status: EnrichmentJobStatus,
    val cueCount: Int,
)

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

data class EnrichmentJobCueResult(
    val cue: SubtitleCue,
    val status: CueEnrichmentStatus,
    val enrichment: CueEnrichment?,
)

data class ClaimedCue(
    val taskId: UUID,
    val jobId: UUID,
    val cue: SubtitleCue,
    val previousCue: SubtitleCue?,
    val nextCue: SubtitleCue?,
    val learningLanguage: LearningLanguage,
    val learnerLevel: CefrLevel,
)
