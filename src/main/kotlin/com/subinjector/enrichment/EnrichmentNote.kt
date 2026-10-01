package com.subinjector.enrichment

data class EnrichmentNote(
    val category: EnrichmentCategory,
    val expression: String,
    val explanation: String,
)
