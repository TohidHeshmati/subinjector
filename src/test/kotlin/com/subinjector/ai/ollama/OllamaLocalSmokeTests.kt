package com.subinjector.ai.ollama

import com.subinjector.ai.LanguageModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_SMOKE_TEST", matches = "true")
class OllamaLocalSmokeTests {
    @Autowired
    private lateinit var languageModel: LanguageModel

    @Test
    fun `generates a response through the configured local Ollama model`() {
        assertEquals("LOCAL_OK", languageModel.generate("Reply with exactly LOCAL_OK"))
    }
}
