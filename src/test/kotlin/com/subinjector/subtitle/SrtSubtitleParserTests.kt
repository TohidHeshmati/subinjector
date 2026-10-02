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
        assertEquals(SubtitleCue(1, 1_000, 2_500, "Guten Morgen.\nWie geht's?"), cues[0])
        assertEquals(SubtitleCue(2, 3_000, 4_000, "Auf Wiedersehen."), cues[1])
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

    @Test
    fun `rejects empty content and content with no cues`() {
        assertThrows(InvalidSubtitleException::class.java) { parser.parse("") }
        assertThrows(InvalidSubtitleException::class.java) { parser.parse("\n  \n") }
    }

    @Test
    fun `rejects an invalid cue number`() {
        assertThrows(InvalidSubtitleException::class.java) {
            parser.parse("zero\n00:00:01,000 --> 00:00:02,000\nHello")
        }
    }

    @Test
    fun `rejects a cue with no timing line`() {
        assertThrows(InvalidSubtitleException::class.java) { parser.parse("1") }
    }

    @Test
    fun `rejects clock values with minutes or seconds above 59`() {
        val invalidTimings = listOf(
            "00:60:00,000 --> 00:61:00,000",
            "00:00:60,000 --> 00:01:01,000",
        )

        invalidTimings.forEach { timing ->
            assertThrows(InvalidSubtitleException::class.java) {
                parser.parse("1\n$timing\nHello")
            }
        }
    }

    @Test
    fun `rejects cue end times equal to or before start time`() {
        val invalidTimings = listOf(
            "00:00:01,000 --> 00:00:01,000",
            "00:00:02,000 --> 00:00:01,000",
        )

        invalidTimings.forEach { timing ->
            assertThrows(InvalidSubtitleException::class.java) {
                parser.parse("1\n$timing\nHello")
            }
        }
    }
}
