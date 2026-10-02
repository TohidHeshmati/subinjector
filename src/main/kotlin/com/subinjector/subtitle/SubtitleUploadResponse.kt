package com.subinjector.subtitle

import com.subinjector.enrichment.EnrichmentJobStatus
import java.util.UUID

data class SubtitleUploadResponse(
    val documentId: UUID,
    val jobId: UUID,
    val status: EnrichmentJobStatus,
    val cueCount: Int,
)
