package com.subinjector.enrichment

import com.subinjector.subtitle.SubtitleEntry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class EnrichmentJobStore(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun createDocument(
        filename: String,
        cues: List<SubtitleEntry>,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): EnrichmentSubmission = transactions.execute {
        val documentId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO subtitle_document (id, source_filename, source_format) VALUES (?, ?, 'SRT')",
            documentId,
            filename,
        )
        persistCues(documentId, cues)
        createJob(documentId, cues.size, learningLanguage, learnerLevel)
    }

    fun createJobForDocument(
        documentId: UUID,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): EnrichmentSubmission = transactions.execute {
        val cueCount = jdbc.queryForObject(
            "SELECT count(*) FROM subtitle_cue WHERE document_id = ?",
            Int::class.java,
            documentId,
        ) ?: 0
        if (cueCount == 0) throw SubtitleDocumentNotFoundException(documentId.toString())
        createJob(documentId, cueCount, learningLanguage, learnerLevel)
    }

    private fun persistCues(documentId: UUID, cues: List<SubtitleEntry>) {
        cues.forEach { cue ->
            jdbc.update(
                """INSERT INTO subtitle_cue (document_id, sequence_number, start_time, end_time, original_text)
                   VALUES (?, ?, ?, ?, ?)""".trimIndent(),
                documentId,
                cue.sequenceNumber,
                cue.startTime,
                cue.endTime,
                cue.text,
            )
        }
    }

    private fun createJob(
        documentId: UUID,
        cueCount: Int,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): EnrichmentSubmission {
        val jobId = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO enrichment_job
               (id, document_id, learning_language, learner_level, status, total_cue_count)
               VALUES (?, ?, ?, ?, 'QUEUED', ?)""".trimIndent(),
            jobId,
            documentId,
            learningLanguage.name,
            learnerLevel.name,
            cueCount,
        )
        jdbc.update(
            """INSERT INTO cue_enrichment (job_id, document_id, cue_id, status)
               SELECT ?, document_id, id, 'PENDING' FROM subtitle_cue
               WHERE document_id = ? ORDER BY sequence_number""".trimIndent(),
            jobId,
            documentId,
        )
        return EnrichmentSubmission(documentId, jobId, EnrichmentJobStatus.QUEUED, cueCount)
    }

    fun claimNextCue(): ClaimedCue? = transactions.execute {
        val row = jdbc.query(
            """SELECT ce.id AS enrichment_id, ce.job_id, j.learning_language, j.learner_level,
                      c.id AS cue_id, c.document_id, c.sequence_number, c.start_time, c.end_time, c.original_text
               FROM cue_enrichment ce
               JOIN enrichment_job j ON j.id = ce.job_id
               JOIN subtitle_cue c ON c.id = ce.cue_id
               WHERE ce.status = 'PENDING' AND j.status IN ('QUEUED', 'PROCESSING')
               ORDER BY j.created_at, c.sequence_number
               LIMIT 1
               FOR UPDATE OF ce SKIP LOCKED""".trimIndent(),
            { rs, _ -> ClaimRow(rs) },
        ).firstOrNull() ?: return@execute null

        jdbc.update(
            "UPDATE cue_enrichment SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            row.enrichmentId,
        )
        jdbc.update(
            "UPDATE enrichment_job SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            row.jobId,
        )
        val previous = findPreviousCue(row.documentId, row.sequenceNumber)
        val next = findNextCue(row.documentId, row.sequenceNumber)
        ClaimedCue(
            jobId = row.jobId,
            cue = row.cue,
            previousCue = previous,
            nextCue = next,
            learningLanguage = row.learningLanguage,
            learnerLevel = row.learnerLevel,
        )
    }

    private fun findPreviousCue(documentId: UUID, sequenceNumber: Int): SubtitleEntry? = jdbc.query(
        """SELECT sequence_number, start_time, end_time, original_text
           FROM subtitle_cue WHERE document_id = ? AND sequence_number < ?
           ORDER BY sequence_number DESC LIMIT 1""".trimIndent(),
        { rs, _ -> rs.toCue() },
        documentId,
        sequenceNumber,
    ).firstOrNull()

    private fun findNextCue(documentId: UUID, sequenceNumber: Int): SubtitleEntry? = jdbc.query(
        """SELECT sequence_number, start_time, end_time, original_text
           FROM subtitle_cue WHERE document_id = ? AND sequence_number > ?
           ORDER BY sequence_number LIMIT 1""".trimIndent(),
        { rs, _ -> rs.toCue() },
        documentId,
        sequenceNumber,
    ).firstOrNull()

    fun saveSuccess(jobId: UUID, sequenceNumber: Int, result: CueEnrichment) {
        val json = objectMapper.writeValueAsString(result)
        transactions.executeWithoutResult {
            jdbc.update(
                """UPDATE cue_enrichment ce SET status = 'SUCCEEDED', enrichment = ?::jsonb, updated_at = CURRENT_TIMESTAMP
                   FROM subtitle_cue c
                   WHERE ce.cue_id = c.id AND ce.job_id = ? AND c.sequence_number = ? AND ce.status = 'PROCESSING'""".trimIndent(),
                json,
                jobId,
                sequenceNumber,
            )
            completeJobIfFinished(jobId)
        }
    }

    fun saveSkipped(jobId: UUID, sequenceNumber: Int) {
        transactions.executeWithoutResult {
            jdbc.update(
                """UPDATE cue_enrichment ce SET status = 'SKIPPED', updated_at = CURRENT_TIMESTAMP
                   FROM subtitle_cue c
                   WHERE ce.cue_id = c.id AND ce.job_id = ? AND c.sequence_number = ? AND ce.status = 'PROCESSING'""".trimIndent(),
                jobId,
                sequenceNumber,
            )
            completeJobIfFinished(jobId)
        }
    }

    private fun completeJobIfFinished(jobId: UUID) {
        jdbc.update(
            """UPDATE enrichment_job SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
               WHERE id = ? AND NOT EXISTS (
                   SELECT 1 FROM cue_enrichment
                   WHERE job_id = ? AND status IN ('PENDING', 'PROCESSING')
               )""".trimIndent(),
            jobId,
            jobId,
        )
    }

    fun recoverInterruptedWork() = transactions.executeWithoutResult {
        jdbc.update(
            "UPDATE cue_enrichment SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP WHERE status = 'PROCESSING'",
        )
        jdbc.update(
            "UPDATE enrichment_job SET status = 'QUEUED', updated_at = CURRENT_TIMESTAMP WHERE status = 'PROCESSING'",
        )
    }

    fun findJob(jobId: UUID): EnrichmentJobProgress? = jdbc.query(
        """SELECT j.id, j.document_id, j.learning_language, j.learner_level, j.status,
                  j.total_cue_count, j.created_at, j.updated_at,
                  count(*) FILTER (WHERE ce.status = 'PENDING') AS pending_count,
                  count(*) FILTER (WHERE ce.status = 'PROCESSING') AS processing_count,
                  count(*) FILTER (WHERE ce.status = 'SUCCEEDED') AS succeeded_count,
                  count(*) FILTER (WHERE ce.status = 'SKIPPED') AS skipped_count
           FROM enrichment_job j JOIN cue_enrichment ce ON ce.job_id = j.id
           WHERE j.id = ?
           GROUP BY j.id""".trimIndent(),
        { rs, _ ->
            EnrichmentJobProgress(
                jobId = rs.getObject("id", UUID::class.java),
                documentId = rs.getObject("document_id", UUID::class.java),
                learningLanguage = LearningLanguage.valueOf(rs.getString("learning_language")),
                learnerLevel = CefrLevel.valueOf(rs.getString("learner_level")),
                status = EnrichmentJobStatus.valueOf(rs.getString("status")),
                totalCueCount = rs.getInt("total_cue_count"),
                pendingCueCount = rs.getInt("pending_count"),
                processingCueCount = rs.getInt("processing_count"),
                succeededCueCount = rs.getInt("succeeded_count"),
                skippedCueCount = rs.getInt("skipped_count"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            )
        },
        jobId,
    ).firstOrNull()

    fun findResults(jobId: UUID): List<EnrichmentJobCueResult> = jdbc.query(
        """SELECT c.sequence_number, c.start_time, c.end_time, c.original_text,
                  ce.status, ce.enrichment
           FROM cue_enrichment ce JOIN subtitle_cue c ON c.id = ce.cue_id
           WHERE ce.job_id = ? ORDER BY c.sequence_number""".trimIndent(),
        { rs, _ ->
            val rawEnrichment = rs.getString("enrichment")
            EnrichmentJobCueResult(
                cue = rs.toCue(),
                status = CueEnrichmentStatus.valueOf(rs.getString("status")),
                enrichment = rawEnrichment?.let { objectMapper.readValue(it, CueEnrichment::class.java) },
            )
        },
        jobId,
    )

    private data class ClaimRow(
        val enrichmentId: UUID,
        val jobId: UUID,
        val documentId: UUID,
        val learningLanguage: LearningLanguage,
        val learnerLevel: CefrLevel,
        val cue: SubtitleEntry,
    ) {
        val sequenceNumber: Int get() = cue.sequenceNumber

        constructor(rs: ResultSet) : this(
            enrichmentId = rs.getObject("enrichment_id", UUID::class.java),
            jobId = rs.getObject("job_id", UUID::class.java),
            documentId = rs.getObject("document_id", UUID::class.java),
            learningLanguage = LearningLanguage.valueOf(rs.getString("learning_language")),
            learnerLevel = CefrLevel.valueOf(rs.getString("learner_level")),
            cue = rs.toCue(),
        )
    }

    private companion object {
        fun ResultSet.toCue() = SubtitleEntry(
            sequenceNumber = getInt("sequence_number"),
            startTime = getString("start_time"),
            endTime = getString("end_time"),
            text = getString("original_text"),
        )
    }
}
