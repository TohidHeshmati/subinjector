package com.subinjector.subtitle.parser

import com.subinjector.subtitle.SubtitleCue
import org.springframework.stereotype.Component

@Component
class SrtSubtitleParser : SubtitleParser {
    override fun parse(content: String): List<SubtitleCue> = SrtParsingSession(content).parse()
}

/** Keeps state local to one parse call; all format-specific parsing rules live here. */
private class SrtParsingSession(content: String) {
    private val lines = content.removePrefix("\uFEFF").lineSequence().toList()
    private val entries = mutableListOf<SubtitleCue>()
    private val textLines = mutableListOf<String>()

    private var state = ParseState.SEQUENCE_NUMBER
    private var sequenceNumber: Int? = null
    private var timing: CueTiming? = null
    private var lineIndex = 0

    fun parse(): List<SubtitleCue> {
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            if (state == ParseState.SEQUENCE_NUMBER && line.isBlank()) {
                lineIndex++
                continue
            }

            when (state) {
                ParseState.SEQUENCE_NUMBER -> readSequenceNumber(line)
                ParseState.TIMING -> readTiming(line)
                ParseState.TEXT -> readText(line)
            }
            lineIndex++
        }

        when (state) {
            ParseState.SEQUENCE_NUMBER -> Unit
            ParseState.TIMING -> throw InvalidSubtitleException("Cue $sequenceNumber is missing its timing line")
            ParseState.TEXT -> finishEntry()
        }

        if (entries.isEmpty()) throw InvalidSubtitleException("Subtitle file contains no cues")
        return entries.toList()
    }

    private fun readSequenceNumber(line: String) {
        sequenceNumber = line.toIntOrNull()?.takeIf { it > 0 }
            ?: throw InvalidSubtitleException("Expected a positive cue number at line ${lineIndex + 1}")
        state = ParseState.TIMING
    }

    private fun readTiming(line: String) {
        timing = parseTiming(line, lineIndex + 1)
        state = ParseState.TEXT
    }

    private fun readText(line: String) {
        if (line.isBlank()) finishEntry() else textLines += line
    }

    private fun finishEntry() {
        val cueTiming = timing ?: throw InvalidSubtitleException("Cue $sequenceNumber is missing its timing line")
        if (textLines.isEmpty()) throw InvalidSubtitleException("Cue $sequenceNumber has no subtitle text")

        entries += SubtitleCue(
            sequenceNumber = sequenceNumber ?: throw InvalidSubtitleException("Cue is missing its number"),
            startMs = cueTiming.startMs,
            endMs = cueTiming.endMs,
            text = textLines.joinToString("\n"),
        )
        sequenceNumber = null
        timing = null
        textLines.clear()
        state = ParseState.SEQUENCE_NUMBER
    }

    private fun parseTiming(line: String, lineNumber: Int): CueTiming {
        val match = TIMING_PATTERN.matchEntire(line)
            ?: throw InvalidSubtitleException("Invalid timing line at line $lineNumber")
        val startMs = parseTimestamp(match.groupValues[1], lineNumber)
        val endMs = parseTimestamp(match.groupValues[2], lineNumber)
        if (endMs <= startMs) {
            throw InvalidSubtitleException("Cue end time must be after its start time at line $lineNumber")
        }
        return CueTiming(startMs, endMs)
    }

    private fun parseTimestamp(timestamp: String, lineNumber: Int): Int {
        val (hours, minutes, secondsAndMillis) = timestamp.split(':')
        val (seconds, millis) = secondsAndMillis.split(',')
        if (minutes.toInt() >= 60 || seconds.toInt() >= 60) {
            throw InvalidSubtitleException("Invalid timestamp at line $lineNumber")
        }
        return ((hours.toInt() * 60 + minutes.toInt()) * 60 + seconds.toInt()) * 1000 + millis.toInt()
    }

    private data class CueTiming(val startMs: Int, val endMs: Int)

    private enum class ParseState {
        SEQUENCE_NUMBER,
        TIMING,
        TEXT,
    }

    private companion object {
        val TIMING_PATTERN = Regex(
            "^(\\d{2}:\\d{2}:\\d{2},\\d{3}) --> (\\d{2}:\\d{2}:\\d{2},\\d{3})$",
        )
    }
}
