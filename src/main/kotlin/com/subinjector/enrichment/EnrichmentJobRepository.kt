package com.subinjector.enrichment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface EnrichmentJobRepository : JpaRepository<EnrichmentJob, UUID> {
    @Query("SELECT j FROM EnrichmentJob j WHERE j.document.id = :documentId ORDER BY j.createdAt DESC")
    fun findByDocumentId(@Param("documentId") documentId: UUID): List<EnrichmentJob>

    @Modifying
    @Query("UPDATE EnrichmentJob j SET j.status = :to, j.updatedAt = :now WHERE j.status = :from")
    fun resetStatus(
        @Param("from") from: EnrichmentJobStatus,
        @Param("to") to: EnrichmentJobStatus,
        @Param("now") now: OffsetDateTime,
    )
}
