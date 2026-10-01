package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleEntry

data class CueEnrichmentRequest(
    val targetCue: SubtitleEntry,
    val previousCue: SubtitleEntry? = null,
    val nextCue: SubtitleEntry? = null,
    val learningLanguage: LearningLanguage = LearningLanguage.GERMAN,
    val learnerLevel: CefrLevel,
)
