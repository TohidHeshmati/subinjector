package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleEntry

data class SubtitleCueEnrichment(
    val cue: SubtitleEntry,
    val status: CueEnrichmentStatus,
    val enrichment: CueEnrichment?,
)
