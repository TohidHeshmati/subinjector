package com.subinjector.subtitle

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
    fun upload(@RequestParam("file") file: MultipartFile): SubtitleUploadResponse {
        val cueCount = subtitleUploadService.upload(file.originalFilename, file.bytes)
        return SubtitleUploadResponse(cueCount = cueCount)
    }
}
