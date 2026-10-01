package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class OllamaLanguageModelConfiguration {
    @Bean
    fun languageModel(
        @Value("\${subinjector.ai.ollama.base-url}") baseUrl: String,
        @Value("\${subinjector.ai.ollama.model}") model: String,
    ): LanguageModel = OllamaLanguageModelAdapter(
        restClient = RestClient.builder().baseUrl(baseUrl).build(),
        model = model,
    )
}
