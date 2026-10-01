package com.subinjector.subtitle

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [SubtitleUploadController::class])
class SubtitleExceptionHandler {
    @ExceptionHandler(InvalidSubtitleException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidSubtitle(exception: InvalidSubtitleException): Map<String, String> =
        mapOf("error" to (exception.message ?: "Invalid subtitle file"))
}
