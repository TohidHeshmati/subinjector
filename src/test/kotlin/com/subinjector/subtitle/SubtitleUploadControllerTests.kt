package com.subinjector.subtitle

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelOutputFormat
import com.subinjector.enrichment.EnrichmentJobWorker
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest(properties = ["subinjector.enrichment.worker.enabled=false"])
@AutoConfigureMockMvc
@Import(SubtitleUploadControllerTests.TestLanguageModelConfiguration::class)
@Transactional
class SubtitleUploadControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var worker: EnrichmentJobWorker

    @Test
    fun `uploads an SRT and returns accepted job then exposes progress and saved cue results`() {
        val response = mockMvc.perform(upload())
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.cueCount").value(1))
            .andReturn()
        val jobId = objectMapper.readTree(response.response.contentAsString)["jobId"].stringValue()

        mockMvc.perform(get("/api/enrichment-jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingCueCount").value(1))

        worker.processOneCue()

        mockMvc.perform(get("/api/enrichment-jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.succeededCueCount").value(1))
        mockMvc.perform(get("/api/enrichment-jobs/$jobId/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].cue.text").value("Hallo"))
            .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$[0].enrichment.notes[0].explanation").value("Hello."))
    }

    @Test
    fun `creates another enrichment job for the same stored document`() {
        val response = mockMvc.perform(upload()).andReturn()
        val documentId = objectMapper.readTree(response.response.contentAsString)["documentId"].stringValue()

        mockMvc.perform(
            post("/api/subtitle-documents/$documentId/enrichment-jobs")
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "A2"),
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.cueCount").value(1))

        assertTrue(response.response.getHeader(HttpHeaders.LOCATION)?.contains("/api/enrichment-jobs/") == true)
    }

    @Test
    fun `skips a failed cue and continues until the job is complete`() {
        val file = MockMultipartFile(
            "file",
            "lesson.srt",
            "application/x-subrip",
            "1\n00:00:01,000 --> 00:00:02,000\nHallo\n\n2\n00:00:02,000 --> 00:00:03,000\nFAIL".toByteArray(),
        )
        val response = mockMvc.perform(
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andReturn()
        val jobId = objectMapper.readTree(response.response.contentAsString)["jobId"].stringValue()

        worker.processOneCue()
        worker.processOneCue()

        mockMvc.perform(get("/api/enrichment-jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.succeededCueCount").value(1))
            .andExpect(jsonPath("$.skippedCueCount").value(1))
        mockMvc.perform(get("/api/enrichment-jobs/$jobId/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$[1].status").value("SKIPPED"))
            .andExpect(jsonPath("$[1].enrichment").doesNotExist())
    }

    @Test
    fun `rejects a non-SRT upload`() {
        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(MockMultipartFile("file", "lesson.txt", "text/plain", "content".toByteArray()))
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects an empty SRT upload`() {
        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(MockMultipartFile("file", "lesson.srt", "application/x-subrip", byteArrayOf()))
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects an SRT upload with no cues`() {
        mockMvc.perform(
            multipart("/api/subtitles/upload")
                .file(MockMultipartFile("file", "lesson.srt", "application/x-subrip", "\n  \n".toByteArray()))
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andExpect(status().isBadRequest)
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
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "FRENCH")
                .param("learnerLevel", "B1"),
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            multipart("/api/subtitles/upload").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "D8"),
        ).andExpect(status().isBadRequest)
    }

    private fun upload() = multipart("/api/subtitles/upload")
        .file(
            MockMultipartFile(
                "file",
                "lesson.srt",
                "application/x-subrip",
                "1\n00:00:01,000 --> 00:00:02,000\nHallo".toByteArray(),
            ),
        )
        .param("learningLanguage", "GERMAN")
        .param("learnerLevel", "B1")

    @TestConfiguration
    class TestLanguageModelConfiguration {
        @Bean
        @Primary
        fun testLanguageModel(): LanguageModel = object : LanguageModel {
            override fun generate(prompt: String, maxOutputTokens: Int, outputFormat: LanguageModelOutputFormat): String {
                val targetCueHasFailureMarker =
                    Regex("targetCue\\s*:\\s*\\{[^}]*text\\s*:\\s*\\\"FAIL\\\"").containsMatchIn(prompt)
                if (targetCueHasFailureMarker) return "not valid JSON"
                return """{"cueNumber":1,"notes":[{"category":"vocabulary","expression":"Hallo","explanation":"Hello."}]}"""
            }
        }
    }
}
