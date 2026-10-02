package com.subinjector.subtitle

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.EnrichmentSubmission
import com.subinjector.enrichment.LearningLanguage
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

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
}
