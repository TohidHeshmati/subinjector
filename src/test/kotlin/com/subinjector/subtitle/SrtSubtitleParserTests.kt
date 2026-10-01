package com.subinjector.subtitle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SrtSubtitleParserTests {
    private val parser = SrtSubtitleParser()

    @Test
    fun `parses cues with multiline text and CRLF line endings`() {
        val content = listOf(
            "1",
            "00:00:01,000 --> 00:00:02,500",
            "Guten Morgen.",
            "Wie geht's?",
            "",
            "2",
            "00:00:03,000 --> 00:00:04,000",
            "Auf Wiedersehen.",
        ).joinToString("\r\n")

        val cues = parser.parse(content)

        assertEquals(2, cues.size)
        assertEquals(SubtitleEntry(1, "00:00:01,000", "00:00:02,500", "Guten Morgen.\nWie geht's?"), cues[0])
        assertEquals(SubtitleEntry(2, "00:00:03,000", "00:00:04,000", "Auf Wiedersehen."), cues[1])
    }

    @Test
    fun `rejects a cue with malformed timing`() {
        val exception = assertThrows(InvalidSubtitleException::class.java) {
            parser.parse("1\nnot a timing line\nHello")
        }

        assertEquals("Invalid timing line at line 2", exception.message)
    }

    @Test
    fun `rejects a cue without text`() {
        assertThrows(InvalidSubtitleException::class.java) {
            parser.parse("1\n00:00:01,000 --> 00:00:02,000\n")
        }
    }
}
