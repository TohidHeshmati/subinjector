package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OllamaLanguageModelConfiguration {
    @Bean
    fun languageModel(
        chatModel: OllamaChatModel,
        @Value("\${spring.ai.ollama.chat.model}") modelName: String,
    ): LanguageModel = OllamaLanguageModelAdapter(chatModel, modelName)
}
