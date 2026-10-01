package com.subinjector.enrichment.prompt

import com.subinjector.enrichment.CueEnrichmentRequest
import com.subinjector.enrichment.LearningLanguage

interface CueEnrichmentPrompt {
    val learningLanguage: LearningLanguage

    fun build(request: CueEnrichmentRequest): String
}
