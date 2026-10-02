package com.subinjector.subtitle

import com.subinjector.ai.LanguageModel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import

@SpringBootTest
@AutoConfigureMockMvc
@Import(SubtitleUploadControllerTests.TestLanguageModelConfiguration::class)
class SubtitleUploadControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `uploads an SRT file and returns original cue data with enrichment`() {
        val file = MockMultipartFile(
            "file",
            "lesson.srt",
            "application/x-subrip",
            "1\n00:00:01,000 --> 00:00:02,000\nHallo".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        )
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"cueCount":1,"succeededCueCount":1,"skippedCueCount":0,"cues":[{"cue":{"sequenceNumber":1,"startTime":"00:00:01,000","endTime":"00:00:02,000","text":"Hallo"},"status":"SUCCEEDED","enrichment":{"cueNumber":1,"notes":[{"category":"VOCABULARY","expression":"Hallo","explanation":"Hello."}]}}]}""",
                ),
            )
    }

    @Test
    fun `rejects a non-SRT upload`() {
        val file = MockMultipartFile("file", "lesson.txt", "text/plain", "content".toByteArray())

        mockMvc.perform(
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects an empty SRT upload`() {
        val file = MockMultipartFile("file", "lesson.srt", "application/x-subrip", byteArrayOf())

        mockMvc.perform(
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects an SRT upload with no cues`() {
        val file = MockMultipartFile("file", "lesson.srt", "application/x-subrip", "\n  \n".toByteArray())

        mockMvc.perform(
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects unsupported learning language and CEFR values`() {
        val file = MockMultipartFile(
            "file",
            "lesson.srt",
            "application/x-subrip",
            "1\n00:00:01,000 --> 00:00:02,000\nHallo".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(file)
                .param("learningLanguage", "FRENCH")
                .param("learnerLevel", "B1"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "D8"),
        ).andExpect(status().isBadRequest)
    }

    @TestConfiguration
    class TestLanguageModelConfiguration {
        @Bean
        @Primary
        fun testLanguageModel(): LanguageModel = object : LanguageModel {
            override fun generate(
                prompt: String,
                maxOutputTokens: Int,
                outputFormat: com.subinjector.ai.LanguageModelOutputFormat,
            ): String = """{"cueNumber":1,"notes":[{"category":"vocabulary","expression":"Hallo","explanation":"Hello."}]}"""
        }
    }
}
