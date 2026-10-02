package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModel
import com.subinjector.ai.LanguageModelException
import com.subinjector.ai.LanguageModelOutputFormat
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaChatOptions

/** Translates the application text-generation port to Ollama's local chat API. */
class OllamaLanguageModelAdapter(
    private val chatModel: OllamaChatModel,
    private val modelName: String,
) : LanguageModel {
    override fun generate(
        prompt: String,
        maxOutputTokens: Int,
        outputFormat: LanguageModelOutputFormat,
    ): String {
        if (prompt.isBlank()) throw LanguageModelException("Prompt must not be blank")
        if (maxOutputTokens <= 0) throw LanguageModelException("Maximum output tokens must be positive")

        val options = OllamaChatOptions.builder()
            .apply {
                model(modelName)
                numPredict(maxOutputTokens)
                keepAlive(KEEP_ALIVE)
                disableThinking()
                if (outputFormat == LanguageModelOutputFormat.JSON) format("json")
            }
            .build()
        val response = try {
            chatModel.call(Prompt(prompt, options))
        } catch (exception: RuntimeException) {
            throw LanguageModelException("Failed to generate text with the configured Ollama model", exception)
        }

        val content = response.result?.output?.text
        return content?.takeIf(String::isNotBlank)
            ?: throw LanguageModelException("Ollama returned no assistant message content")
    }

    private companion object {
        const val KEEP_ALIVE = "5m"
    }
}
