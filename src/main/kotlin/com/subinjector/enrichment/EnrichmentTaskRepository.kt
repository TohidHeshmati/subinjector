package com.subinjector.enrichment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface EnrichmentTaskRepository : JpaRepository<EnrichmentTask, UUID> {
    @Query(value = """
        SELECT t.id FROM enrichment_task t
        JOIN enrichment_job j ON j.id = t.job_id
        JOIN subtitle_cue c ON c.id = t.cue_id
        WHERE t.status = 'PENDING' AND j.status IN ('QUEUED', 'PROCESSING')
        ORDER BY j.created_at ASC, c.sequence_number ASC
        LIMIT 1
        FOR UPDATE OF t SKIP LOCKED
    """, nativeQuery = true)
    fun lockNextPendingTaskId(): UUID?

    @Query("SELECT t FROM EnrichmentTask t JOIN FETCH t.cue WHERE t.job.id = :jobId ORDER BY t.cue.sequenceNumber")
    fun findByJobIdWithCues(@Param("jobId") jobId: UUID): List<EnrichmentTask>

    @Query("SELECT COUNT(t) FROM EnrichmentTask t WHERE t.job.id = :jobId AND t.status = :status")
    fun countByJobIdAndStatus(@Param("jobId") jobId: UUID, @Param("status") status: CueEnrichmentStatus): Long

    fun existsByJobAndStatusIn(job: EnrichmentJob, statuses: List<CueEnrichmentStatus>): Boolean

    @Modifying
    @Query("UPDATE EnrichmentTask t SET t.status = :to, t.updatedAt = :now WHERE t.status = :from")
    fun resetStatus(
        @Param("from") from: CueEnrichmentStatus,
        @Param("to") to: CueEnrichmentStatus,
        @Param("now") now: OffsetDateTime,
    )
}
