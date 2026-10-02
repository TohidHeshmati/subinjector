package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleCue

data class CueEnrichmentRequest(
    val targetCue: SubtitleCue,
    val previousCue: SubtitleCue? = null,
    val nextCue: SubtitleCue? = null,
    val learningLanguage: LearningLanguage,
    val learnerLevel: CefrLevel,
)
