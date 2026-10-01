package com.subinjector.subtitle

/** One ordered, timed piece of subtitle text. */
data class SubtitleEntry(
    val sequenceNumber: Int,
    val startTime: String,
    val endTime: String,
    val text: String,
)
