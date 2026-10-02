package com.subinjector.ai

interface LanguageModel {
    fun generate(
        prompt: String,
        maxOutputTokens: Int,
        outputFormat: LanguageModelOutputFormat = LanguageModelOutputFormat.TEXT,
    ): String
}
