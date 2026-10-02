package com.subinjector.enrichment

import com.subinjector.BaseEntity
import com.subinjector.subtitle.SubtitleCueEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "enrichment_task")
class EnrichmentTask(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    val job: EnrichmentJob,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cue_id")
    val cue: SubtitleCueEntity,
    @Enumerated(EnumType.STRING)
    var status: CueEnrichmentStatus = CueEnrichmentStatus.PENDING,
    var attempts: Int = 0,
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var result: String? = null,
    var error: String? = null,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) : BaseEntity()
