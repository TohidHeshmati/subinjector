package com.subinjector.subtitle

/** Parses subtitle text into ordered cues, independently of how the text was received. */
interface SubtitleParser {
    fun parse(content: String): List<SubtitleEntry>
}
