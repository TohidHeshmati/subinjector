package com.subinjector.enrichment.job

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api")
class EnrichmentJobController(private val service: EnrichmentJobService) {
    @GetMapping("/subtitle-documents/{documentId}/enrichment-jobs")
    fun listJobs(@PathVariable documentId: UUID): List<EnrichmentJobProgress> =
        service.listJobsForDocument(documentId)

    @PostMapping("/subtitle-documents/{documentId}/enrichment-jobs")
    fun createJob(
        @PathVariable documentId: UUID,
        @RequestParam learningLanguage: LearningLanguage,
        @RequestParam learnerLevel: CefrLevel,
    ): ResponseEntity<EnrichmentSubmission> {
        val submission = service.createJob(documentId, learningLanguage, learnerLevel)
        val statusUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/enrichment-jobs/{jobId}")
            .buildAndExpand(submission.jobId)
            .toUriString()
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, statusUrl)
            .body(submission)
    }

    @GetMapping("/enrichment-jobs/{jobId}")
    fun getJob(@PathVariable jobId: UUID): EnrichmentJobProgress = service.getJob(jobId)

    @PostMapping("/enrichment-jobs/{jobId}/retry")
    fun retry(@PathVariable jobId: UUID): ResponseEntity<EnrichmentJobProgress> =
        ResponseEntity.accepted().body(service.retrySkippedTasks(jobId))

    @GetMapping("/enrichment-jobs/{jobId}/results")
    fun getResults(@PathVariable jobId: UUID): List<EnrichmentJobCueResult> = service.getResults(jobId)
}
