package com.subinjector.subtitle

import java.time.OffsetDateTime
import java.util.UUID

interface SubtitleDocumentSummary {
    val documentId: UUID
    val filename: String
    val format: String
    val cueCount: Long
    val createdAt: OffsetDateTime
}
