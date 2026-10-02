package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleEntry
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
    val learningLanguage: LearningLanguage,
    val learnerLevel: CefrLevel,
    val status: EnrichmentJobStatus,
    val totalCueCount: Int,
    val pendingCueCount: Int,
    val processingCueCount: Int,
    val succeededCueCount: Int,
    val skippedCueCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class EnrichmentJobCueResult(
    val cue: SubtitleEntry,
    val status: CueEnrichmentStatus,
    val enrichment: CueEnrichment?,
)

data class ClaimedCue(
    val jobId: UUID,
    val cue: SubtitleEntry,
    val previousCue: SubtitleEntry?,
    val nextCue: SubtitleEntry?,
    val learningLanguage: LearningLanguage,
    val learnerLevel: CefrLevel,
)
