package com.subinjector.ai

/** Application-facing port for bounded text generation without depending on a specific AI provider. */
interface LanguageModel {
    /** The provider must stop generation after at most [maxOutputTokens] tokens. */
    fun generate(
        prompt: String,
        maxOutputTokens: Int,
        outputFormat: LanguageModelOutputFormat = LanguageModelOutputFormat.TEXT,
    ): String
}
