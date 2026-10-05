package com.subinjector.enrichment.job

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.subtitle.SubtitleCue
import java.util.UUID

data class ClaimedCue(
    val taskId: UUID,
    val jobId: UUID,
    val cue: SubtitleCue,
    val previousCue: SubtitleCue?,
    val nextCue: SubtitleCue?,
    val learningLanguage: LearningLanguage,
    val learnerLevel: CefrLevel,
)
