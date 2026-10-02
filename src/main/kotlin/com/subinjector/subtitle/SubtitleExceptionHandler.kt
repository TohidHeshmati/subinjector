package com.subinjector.subtitle

import com.subinjector.enrichment.EnrichmentJobNotFoundException
import com.subinjector.enrichment.SubtitleDocumentNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SubtitleExceptionHandler {
    @ExceptionHandler(InvalidSubtitleException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidSubtitle(exception: InvalidSubtitleException): Map<String, String> =
        mapOf("error" to (exception.message ?: "Invalid subtitle file"))

    @ExceptionHandler(
        EnrichmentJobNotFoundException::class,
        SubtitleDocumentNotFoundException::class,
    )
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissingResource(exception: RuntimeException): Map<String, String> =
        mapOf("error" to (exception.message ?: "Requested resource was not found"))
}
