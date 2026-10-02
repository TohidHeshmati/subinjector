package com.subinjector.subtitle

import com.subinjector.enrichment.SubtitleCueEnrichment

data class SubtitleUploadResponse(
    val cueCount: Int,
    val succeededCueCount: Int,
    val skippedCueCount: Int,
    val cues: List<SubtitleCueEnrichment>,
)
