package com.subinjector.subtitle.parser

import com.subinjector.subtitle.SubtitleCue

interface SubtitleParser {
    fun parse(content: String): List<SubtitleCue>
}
