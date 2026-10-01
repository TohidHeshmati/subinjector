package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModelException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
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
            .andExpect(
                content().json(
                    """
                    {
                      "model": "$MODEL",
                      "messages": [{"role":"user","content":"Reply with exactly LOCAL_OK"}],
                      "stream": false,
                      "think": false,
                      "keep_alive": 0
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("""{"message":{"content":"LOCAL_OK"},"done":true}""", MediaType.APPLICATION_JSON))

        assertEquals("LOCAL_OK", model.generate("Reply with exactly LOCAL_OK"))
        server.verify()
    }

    @Test
    fun `rejects a blank prompt without making a request`() {
        assertThrows(LanguageModelException::class.java) { model.generate("  ") }
        server.verify()
    }

    @Test
    fun `wraps an Ollama service error`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertThrows(LanguageModelException::class.java) { model.generate("hello") }
        server.verify()
    }

    @Test
    fun `rejects a response without assistant content`() {
        server.expect(requestTo("$OLLAMA_URL/api/chat"))
            .andRespond(withSuccess("""{"done":true}""", MediaType.APPLICATION_JSON))

        assertThrows(LanguageModelException::class.java) { model.generate("hello") }
        server.verify()
    }

    private companion object {
        const val OLLAMA_URL = "http://localhost:11434"
        const val MODEL = "qwen3:0.6b"
    }
}
