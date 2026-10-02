package com.subinjector.subtitle

import com.subinjector.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "subtitle_document")
class SubtitleDocument(
    val filename: String,
    val format: String,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) : BaseEntity()
