package com.subinjector.enrichment.job

import com.subinjector.enrichment.CueEnrichment
import com.subinjector.subtitle.SubtitleCue

data class EnrichmentJobCueResult(
    val cue: SubtitleCue,
    val status: EnrichmentTaskStatus,
    val enrichment: CueEnrichment?,
)
