package com.subinjector.enrichment.job

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.shared.BaseEntity
import com.subinjector.subtitle.SubtitleDocument
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "enrichment_job")
class EnrichmentJob(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    val document: SubtitleDocument,
    @Enumerated(EnumType.STRING)
    val language: LearningLanguage,
    @Enumerated(EnumType.STRING)
    val level: CefrLevel,
    val cueCount: Int,
    @Enumerated(EnumType.STRING)
    var status: EnrichmentJobStatus = EnrichmentJobStatus.QUEUED,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) : BaseEntity()
