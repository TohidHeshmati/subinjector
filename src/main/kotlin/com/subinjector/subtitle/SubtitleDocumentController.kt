package com.subinjector.subtitle

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.enrichment.job.EnrichmentSubmission
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/subtitle-documents")
class SubtitleDocumentController(private val service: SubtitleDocumentService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("learningLanguage") learningLanguage: LearningLanguage,
        @RequestParam("learnerLevel") learnerLevel: CefrLevel,
    ): ResponseEntity<EnrichmentSubmission> {
        val submission = service.create(
            filename = file.originalFilename,
            content = file.bytes,
            learningLanguage = learningLanguage,
            learnerLevel = learnerLevel,
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, "/api/enrichment-jobs/${submission.jobId}")
            .body(submission)
    }

    @GetMapping
    fun list(): List<SubtitleDocumentSummary> = service.list()

    @GetMapping("/{documentId}")
    fun get(@PathVariable documentId: UUID): SubtitleDocumentSummary = service.get(documentId)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable documentId: UUID) = service.delete(documentId)
}
