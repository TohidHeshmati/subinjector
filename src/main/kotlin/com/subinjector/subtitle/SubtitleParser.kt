package com.subinjector.subtitle

interface SubtitleParser {
    fun parse(content: String): List<SubtitleCue>
}
