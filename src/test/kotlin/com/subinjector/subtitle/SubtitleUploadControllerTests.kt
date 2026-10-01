package com.subinjector.subtitle

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SubtitleUploadControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `uploads an SRT file and returns its cue count`() {
        val file = MockMultipartFile(
            "file",
            "lesson.srt",
            "application/x-subrip",
            "1\n00:00:01,000 --> 00:00:02,000\nHallo\n\n2\n00:00:03,000 --> 00:00:04,000\nTschuss".toByteArray(),
        )

        mockMvc.perform(multipart("/api/subtitles/upload").file(file))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"cueCount":2}"""))
    }

    @Test
    fun `rejects a non-SRT upload`() {
        val file = MockMultipartFile("file", "lesson.txt", "text/plain", "content".toByteArray())

        mockMvc.perform(multipart("/api/subtitles/upload").file(file))
            .andExpect(status().isBadRequest)
    }
}
