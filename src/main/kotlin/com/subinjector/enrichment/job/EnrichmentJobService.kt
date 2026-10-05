package com.subinjector.enrichment.job

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.CueEnrichment
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.subtitle.SubtitleCueRepository
import com.subinjector.subtitle.SubtitleDocument
import com.subinjector.subtitle.SubtitleDocumentNotFoundException
import com.subinjector.subtitle.SubtitleDocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EnrichmentJobService(
    private val documentRepository: SubtitleDocumentRepository,
    private val cueRepository: SubtitleCueRepository,
    private val jobRepository: EnrichmentJobRepository,
    private val taskRepository: EnrichmentTaskRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun createJob(documentId: UUID, language: LearningLanguage, level: CefrLevel): EnrichmentSubmission {
        val document = documentRepository.findById(documentId)
            .orElseThrow { SubtitleDocumentNotFoundException(documentId.toString()) }
        val cueCount = cueRepository.countByDocument(document).toInt()
        if (cueCount == 0) throw SubtitleDocumentNotFoundException(documentId.toString())
        return createJobForDocument(document, cueCount, language, level)
    }

    @Transactional(readOnly = true)
    fun listJobsForDocument(documentId: UUID): List<EnrichmentJobProgress> {
        if (!documentRepository.existsById(documentId)) throw SubtitleDocumentNotFoundException(documentId.toString())
        return jobRepository.findByDocumentId(documentId).map { it.toProgress() }
    }

    @Transactional(readOnly = true)
    fun getJob(jobId: UUID): EnrichmentJobProgress {
        val job = jobRepository.findById(jobId).orElseThrow { EnrichmentJobNotFoundException(jobId.toString()) }
        return job.toProgress()
    }

    @Transactional(readOnly = true)
    fun getResults(jobId: UUID): List<EnrichmentJobCueResult> {
        if (!jobRepository.existsById(jobId)) throw EnrichmentJobNotFoundException(jobId.toString())
        return taskRepository.findByJobIdWithCues(jobId).map { task ->
            val rawResult = task.result
            EnrichmentJobCueResult(
                cue = task.cue.toDomain(),
                status = task.status,
                enrichment = rawResult?.let { objectMapper.readValue(it, CueEnrichment::class.java) },
            )
        }
    }

    @Transactional
    fun claimNextTask(): ClaimedCue? {
        val taskId = taskRepository.lockNextPendingTaskId() ?: return null
        val task = taskRepository.findById(taskId).orElse(null) ?: return null
        task.status = EnrichmentTaskStatus.PROCESSING
        task.attempts += 1
        task.updatedAt = OffsetDateTime.now()
        task.job.status = EnrichmentJobStatus.PROCESSING
        task.job.updatedAt = OffsetDateTime.now()

        val cue = task.cue
        val previous = cueRepository.findPreviousCue(cue.document, cue.sequenceNumber).firstOrNull()
        val next = cueRepository.findNextCue(cue.document, cue.sequenceNumber).firstOrNull()

        return ClaimedCue(
            taskId = task.id,
            jobId = task.job.id,
            cue = cue.toDomain(),
            previousCue = previous?.toDomain(),
            nextCue = next?.toDomain(),
            learningLanguage = task.job.language,
            learnerLevel = task.job.level,
        )
    }

    @Transactional
    fun saveSuccess(taskId: UUID, enrichment: CueEnrichment) {
        val task = taskRepository.findById(taskId).orElseThrow()
        task.status = EnrichmentTaskStatus.SUCCEEDED
        task.result = objectMapper.writeValueAsString(enrichment)
        task.updatedAt = OffsetDateTime.now()
        completeJobIfFinished(task.job)
    }

    @Transactional
    fun saveSkipped(taskId: UUID, reason: String) {
        val task = taskRepository.findById(taskId).orElseThrow()
        task.status = EnrichmentTaskStatus.SKIPPED
        task.error = reason
        task.updatedAt = OffsetDateTime.now()
        completeJobIfFinished(task.job)
    }

    @Transactional
    fun retrySkippedTasks(jobId: UUID): EnrichmentJobProgress {
        val job = jobRepository.findById(jobId).orElseThrow { EnrichmentJobNotFoundException(jobId.toString()) }
        if (job.status != EnrichmentJobStatus.COMPLETED) throw EnrichmentJobNotRetryableException(jobId.toString())
        val now = OffsetDateTime.now()
        val retried = taskRepository.retrySkippedByJobId(jobId, now, EnrichmentTaskStatus.PENDING, EnrichmentTaskStatus.SKIPPED, MAX_TASK_ATTEMPTS)
        if (retried > 0) {
            job.status = EnrichmentJobStatus.QUEUED
            job.updatedAt = now
        }
        return job.toProgress()
    }

    @Transactional
    fun recoverInterruptedWork() {
        val now = OffsetDateTime.now()
        taskRepository.resetStatus(EnrichmentTaskStatus.PROCESSING, EnrichmentTaskStatus.PENDING, now)
        jobRepository.resetStatus(EnrichmentJobStatus.PROCESSING, EnrichmentJobStatus.QUEUED, now)
    }

    private companion object {
        const val MAX_TASK_ATTEMPTS = 3
    }

    @Transactional
    fun createJobForDocument(
        document: SubtitleDocument,
        cueCount: Int,
        language: LearningLanguage,
        level: CefrLevel,
    ): EnrichmentSubmission {
        val job = jobRepository.save(EnrichmentJob(document, language, level, cueCount))
        val cueEntities = cueRepository.findAllByDocumentOrderBySequenceNumber(document)
        val tasks = cueEntities.map { cue -> EnrichmentTask(job = job, cue = cue) }
        taskRepository.saveAll(tasks)
        return EnrichmentSubmission(document.id, job.id, EnrichmentJobStatus.QUEUED, cueCount)
    }

    private fun completeJobIfFinished(job: EnrichmentJob) {
        val stillActive = taskRepository.existsByJobAndStatusIn(
            job,
            listOf(EnrichmentTaskStatus.PENDING, EnrichmentTaskStatus.PROCESSING),
        )
        if (!stillActive) {
            job.status = EnrichmentJobStatus.COMPLETED
            job.updatedAt = OffsetDateTime.now()
        }
    }

    private fun EnrichmentJob.toProgress(): EnrichmentJobProgress {
        val jobId = this.id
        return EnrichmentJobProgress(
            jobId = jobId,
            documentId = document.id,
            language = language,
            level = level,
            status = status,
            cueCount = cueCount,
            pendingCueCount = taskRepository.countByJobIdAndStatus(jobId, EnrichmentTaskStatus.PENDING).toInt(),
            processingCueCount = taskRepository.countByJobIdAndStatus(jobId, EnrichmentTaskStatus.PROCESSING).toInt(),
            succeededCueCount = taskRepository.countByJobIdAndStatus(jobId, EnrichmentTaskStatus.SUCCEEDED).toInt(),
            skippedCueCount = taskRepository.countByJobIdAndStatus(jobId, EnrichmentTaskStatus.SKIPPED).toInt(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
