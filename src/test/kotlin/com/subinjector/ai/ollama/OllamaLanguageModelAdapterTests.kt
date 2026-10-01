package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModelException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OllamaLanguageModelAdapterTests {
    private lateinit var server: MockRestServiceServer
    private lateinit var model: OllamaLanguageModelAdapter

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        model = OllamaLanguageModelAdapter(builder.baseUrl(OLLAMA_URL).build(), MODEL)
    }

    @Test
    fun `sends a non-streaming chat request and returns assistant content`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(
                content().json(
                    """
                    {
                      "model": "$MODEL",
                      "messages": [{"role":"user","content":"Reply with exactly LOCAL_OK"}],
                      "stream": false,
                      "think": false,
                      "keep_alive": 0,
                      "options": {"num_predict":64}
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("""{"message":{"content":"LOCAL_OK"},"done":true}""", MediaType.APPLICATION_JSON))

        assertEquals("LOCAL_OK", model.generate("Reply with exactly LOCAL_OK", maxOutputTokens = 64))
        server.verify()
    }

    @Test
    fun `rejects a blank prompt without making a request`() {
        assertThrows(LanguageModelException::class.java) { model.generate("  ", maxOutputTokens = 64) }
        server.verify()
    }

    @Test
    fun `rejects a non-positive output token limit without making a request`() {
        listOf(0, -1).forEach { maxOutputTokens ->
            assertThrows(LanguageModelException::class.java) {
                model.generate("hello", maxOutputTokens = maxOutputTokens)
            }
        }
        server.verify()
    }

    @Test
    fun `wraps an Ollama service error`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertThrows(LanguageModelException::class.java) { model.generate("hello", maxOutputTokens = 64) }
        server.verify()
    }

    @Test
    fun `rejects a response without assistant content`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withSuccess("""{"done":true}""", MediaType.APPLICATION_JSON))

        assertThrows(LanguageModelException::class.java) { model.generate("hello", maxOutputTokens = 64) }
        server.verify()
    }

    @Test
    fun `rejects a response with non-text assistant content`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withSuccess("""{"message":{"content":42},"done":true}""", MediaType.APPLICATION_JSON))

        assertThrows(LanguageModelException::class.java) { model.generate("hello", maxOutputTokens = 64) }
        server.verify()
    }

    @Test
    fun `wraps a malformed JSON response`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON))

        assertThrows(LanguageModelException::class.java) { model.generate("hello", maxOutputTokens = 64) }
        server.verify()
    }

    @Test
    fun `serializes quotes and newlines in the prompt as JSON content`() {
        val prompt = "Er sagte: \"Hallo\"\nWie geht's?"
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andExpect(
                content().json(
                    """
                    {
                      "model": "$MODEL",
                      "messages": [{"role":"user","content":"Er sagte: \"Hallo\"\nWie geht's?"}],
                      "stream": false,
                      "think": false,
                      "keep_alive": 0,
                      "options": {"num_predict":64}
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("""{"message":{"content":"ok"}}""", MediaType.APPLICATION_JSON))

        assertEquals("ok", model.generate(prompt, maxOutputTokens = 64))
        server.verify()
    }

    private companion object {
        const val OLLAMA_URL = "http://localhost:11434"
        const val MODEL = "qwen3:0.6b"
    }
}
