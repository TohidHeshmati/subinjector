package com.subinjector.enrichment.job

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelOutputFormat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest(properties = ["subinjector.enrichment.worker.enabled=false"])
@AutoConfigureMockMvc
@Import(EnrichmentJobRetryTests.TestLanguageModelConfiguration::class)
class EnrichmentJobRetryTests {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var worker: EnrichmentJobWorker
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE enrichment_task, enrichment_job, subtitle_cue, subtitle_document CASCADE")
    }

    @Test
    fun `retries skipped cues and re-queues the job`() {
        val jobId = uploadWithSkippedCue()
        worker.processOneCue()
        worker.processOneCue()

        mockMvc.perform(get("/api/enrichment-jobs/$jobId"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.skippedCueCount").value(1))

        mockMvc.perform(post("/api/enrichment-jobs/$jobId/retry"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.pendingCueCount").value(1))
            .andExpect(jsonPath("$.skippedCueCount").value(0))
    }

    @Test
    fun `permanently skips a cue after the maximum number of attempts`() {
        val jobId = uploadWithSkippedCue()

        repeat(3) { attempt ->
            worker.processOneCue()
            worker.processOneCue()

            val retryStatus = if (attempt < 2) "QUEUED" else "COMPLETED"
            val retryPending = if (attempt < 2) 1 else 0
            val retrySkipped = if (attempt < 2) 0 else 1

            mockMvc.perform(post("/api/enrichment-jobs/$jobId/retry"))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.status").value(retryStatus))
                .andExpect(jsonPath("$.pendingCueCount").value(retryPending))
                .andExpect(jsonPath("$.skippedCueCount").value(retrySkipped))
        }

        mockMvc.perform(get("/api/enrichment-jobs/$jobId"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.succeededCueCount").value(1))
            .andExpect(jsonPath("$.skippedCueCount").value(1))
    }

    @Test
    fun `retry on a job with no skipped cues is a no-op`() {
        val response = mockMvc.perform(
            multipart("/api/subtitle-documents")
                .file(MockMultipartFile("file", "lesson.srt", "application/x-subrip",
                    "1\n00:00:01,000 --> 00:00:02,000\nHallo".toByteArray()))
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andReturn()
        val jobId = objectMapper.readTree(response.response.contentAsString)["jobId"].stringValue()
        worker.processOneCue()

        mockMvc.perform(post("/api/enrichment-jobs/$jobId/retry"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.succeededCueCount").value(1))
    }

    @Test
    fun `returns 409 when retrying a job that is not yet completed`() {
        val response = mockMvc.perform(
            multipart("/api/subtitle-documents")
                .file(MockMultipartFile("file", "lesson.srt", "application/x-subrip",
                    "1\n00:00:01,000 --> 00:00:02,000\nHallo".toByteArray()))
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andReturn()
        val jobId = objectMapper.readTree(response.response.contentAsString)["jobId"].stringValue()

        mockMvc.perform(post("/api/enrichment-jobs/$jobId/retry"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `returns 404 when retrying a missing job`() {
        mockMvc.perform(post("/api/enrichment-jobs/00000000-0000-0000-0000-000000000000/retry"))
            .andExpect(status().isNotFound)
    }

    private fun uploadWithSkippedCue(): String {
        val file = MockMultipartFile(
            "file", "lesson.srt", "application/x-subrip",
            "1\n00:00:01,000 --> 00:00:02,000\nHallo\n\n2\n00:00:02,000 --> 00:00:03,000\nFAIL".toByteArray(),
        )
        val response = mockMvc.perform(
            multipart("/api/subtitle-documents").file(file)
                .param("learningLanguage", "GERMAN")
                .param("learnerLevel", "B1"),
        ).andReturn()
        return objectMapper.readTree(response.response.contentAsString)["jobId"].stringValue()
    }

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
