package com.subinjector.ai

/** Application-facing port for generating text without depending on a specific AI provider. */
interface LanguageModel {
    fun generate(prompt: String): String
}
