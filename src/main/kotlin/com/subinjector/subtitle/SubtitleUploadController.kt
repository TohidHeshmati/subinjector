package com.subinjector.subtitle

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.LearningLanguage
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/subtitles")
class SubtitleUploadController(private val subtitleUploadService: SubtitleUploadService) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("learningLanguage") learningLanguage: LearningLanguage,
        @RequestParam("learnerLevel") learnerLevel: CefrLevel,
    ): SubtitleUploadResponse = subtitleUploadService.upload(
        filename = file.originalFilename,
        content = file.bytes,
        learningLanguage = learningLanguage,
        learnerLevel = learnerLevel,
    )
}
