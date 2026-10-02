package com.subinjector.subtitle

data class SubtitleCue(
    val sequenceNumber: Int,
    val startMs: Int,
    val endMs: Int,
    val text: String,
)
