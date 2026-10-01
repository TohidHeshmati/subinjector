package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelException
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** Translates the application text-generation port to Ollama's local chat API. */
class OllamaLanguageModelAdapter(
    private val restClient: RestClient,
    private val model: String,
) : LanguageModel {
    override fun generate(prompt: String): String {
        if (prompt.isBlank()) throw LanguageModelException("Prompt must not be blank")

        val response = try {
            restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "model" to model,
                        "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                        "stream" to false,
                        "think" to false,
                        "keep_alive" to 0,
                    ),
                )
                .retrieve()
                .body(Map::class.java)
        } catch (exception: RestClientException) {
            throw LanguageModelException("Failed to communicate with the configured Ollama service", exception)
        }

        val message = response?.get("message") as? Map<*, *>
        val content = message?.get("content") as? String
        return content?.takeIf(String::isNotBlank)
            ?: throw LanguageModelException("Ollama returned no assistant message content")
    }
}
